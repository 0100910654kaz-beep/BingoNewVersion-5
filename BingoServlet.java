package servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/BingoServlet")
public class BingoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // 🏢 サーバー全体で複数の部屋を完全に分けて管理する「無敵の下駄箱（Map）」
    @SuppressWarnings("unchecked")
    private synchronized Map<String, BingoGame> getGamesMap(ServletContext application) {
        Map<String, BingoGame> games = (Map<String, BingoGame>) application.getAttribute("games");
        if (games == null) {
            games = new ConcurrentHashMap<>();
            application.setAttribute("games", games);
        }
        return games;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");
        String userType = request.getParameter("userType"); 
        ServletContext application = getServletContext();
        HttpSession session = request.getSession();
        
        // 📦 下駄箱（Map）を確実に取得
        Map<String, BingoGame> games = getGamesMap(application);
        
        // 🔑 このブラウザ（司会者またはプレイヤー）が今どの部屋IDにいるかをセッションから取得
        String sessionGameId = (String) session.getAttribute("myCurrentGameId");
        
        // ⏱️ 1. 定期自動期限チェック（有効期限切れ、または終了後の部屋を安全に掃除）
        if (!games.isEmpty()) {
            games.entrySet().removeIf(entry -> {
                BingoGame g = entry.getValue();
                return g.isExpired() || g.isPast2HoursFromLastBingo();
            });
        }

        // 🚀 2. セッションからプレイヤー名を取得
        String confirmedName = (String) session.getAttribute("myConfirmedName");
        
        // 司会者でもなく、アクションもなく、どこの部屋にも属していない初期状態ならトップへ戻す
        if (action == null && !"admin".equals(userType) && sessionGameId == null) {
            response.sendRedirect("index.jsp");
            return;
        }

        // 🛠️ 3. 司会者(admin)向けの完全独立処理
        if ("admin".equals(userType)) {
            BingoGame currentGame = null;
            
            // 既存の紐付けがあれば、必ず「その部屋ID」のデータを下駄箱から1対1で取り出す
            if (sessionGameId != null) {
                currentGame = games.get(sessionGameId);
            }

            if ("create".equals(action)) {
                // 新規に部屋を作成する処理
                String validDaysParam = request.getParameter("validDays");
                int validDays = 8; // デフォルト値
                if (validDaysParam != null && !validDaysParam.isEmpty()) {
                    try {
                        validDays = Integer.parseInt(validDaysParam);
                    } catch (NumberFormatException e) {
                        validDays = 8;
                    }
                }
                
                // 絶対に他と被らない4桁の部屋番号(ID)を自動生成
                String newGameId;
                do {
                    newGameId = String.format("%04d", (int)(Math.random() * 10000));
                } while (games.containsKey(newGameId));
                
                // 新しい部屋を作成して下駄箱にガッチリ保管
                currentGame = new BingoGame(newGameId, validDays);
                games.put(newGameId, currentGame);
                
                sessionGameId = newGameId;
                session.setAttribute("myCurrentGameId", sessionGameId); // 司会者ブラウザにこの部屋IDを固定
                
            } else if ("draw".equals(action)) {
                // 【バグ完全修正】必ず「今自分が管理している部屋」に対してのみ玉を引く！！
                if (currentGame != null) {
                    List<Integer> pool = new ArrayList<>();
                    for (int i = 1; i <= 75; i++) {
                        if (!currentGame.getDrawnNumbers().contains(i)) {
                            pool.add(i);
                        }
                    }
                    if (!pool.isEmpty()) {
                        Collections.shuffle(pool);
                        int drawn = pool.get(0);
                        currentGame.getDrawnNumbers().add(drawn);
                        currentGame.checkPlayersStatus(drawn); // この部屋の参加者だけを当選判定
                    }
                }
            } else if ("reset".equals(action)) {
                // 【バグ完全修正】リセット時も、他の部屋は一切触らず、この部屋だけを下駄箱から削除する
                if (sessionGameId != null) {
                    games.remove(sessionGameId);
                    session.removeAttribute("myCurrentGameId");
                    sessionGameId = null;
                    currentGame = null;
                }
            }

            // 【バグ完全修正】他の部屋のデータに上書きされるのを完全に防ぎ、自分の部屋データだけを確実に画面へ渡す
            request.setAttribute("game", currentGame);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 👤 4. 一般プレイヤー向けの完全独立処理
        if ("join".equals(action)) {
            String inputGameId = request.getParameter("gameId");
            String inputName = request.getParameter("playerName");

            if (inputName == null || inputName.trim().isEmpty()) {
                request.setAttribute("error", "⚠️ 名前を入力してください。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
            inputName = inputName.trim();

            // 参加者が入力した4桁IDの部屋が、下駄箱（Map）に実在するかチェック
            if (inputGameId != null && games.containsKey(inputGameId)) {
                BingoGame targetGame = games.get(inputGameId);
                
                // もし過去に別の部屋に入っていた場合は、古い部屋の名簿から削除
                if (confirmedName != null && !confirmedName.equals(inputName) && sessionGameId != null) {
                    BingoGame oldGame = games.get(sessionGameId);
                    if (oldGame != null) {
                        oldGame.getAllPlayers().remove(confirmedName);
                    }
                }
                
                // 指定された部屋の名簿にプレイヤーを追加
                if (!targetGame.getAllPlayers().contains(inputName)) {
                    targetGame.getAllPlayers().add(inputName);
                }
                
                confirmedName = inputName;
                sessionGameId = inputGameId;
                
                session.setAttribute("myConfirmedName", confirmedName);
                session.setAttribute("myCurrentGameId", sessionGameId); // プレイヤーブラウザにこの部屋IDを記憶
            } else {
                request.setAttribute("error", "⚠️ 部屋番号（ゲームID）が正しくありません、または有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        // 【バグ完全修正】プレイヤーの画面自動更新時も、必ず自分が所属している部屋のデータだけをピンポイントで取得する
        BingoGame currentGame = null;
        if (sessionGameId != null) {
            currentGame = games.get(sessionGameId);
        }

        // 司会者がその部屋をリセットした、または部屋自体が消えた場合はカードを破棄
        if (currentGame == null || currentGame.getDrawnNumbers().isEmpty()) {
            session.removeAttribute("card");
        }

        // 現在の有効なカードをセッションから取得してサーバー側に再同期
        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        if (card != null && confirmedName != null && !confirmedName.isEmpty() && currentGame != null) {
            currentGame.setPlayerCard(confirmedName, card);
        }

        // 🚚 5. プレイヤー画面（index.jsp）に必要なオブジェクトを載せてフォワード
        if (confirmedName == null) {
            confirmedName = request.getParameter("playerName");
        }
        request.setAttribute("game", currentGame);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.getRequestDispatcher("index.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}
