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

    // 🏢 サーバー全体で複数の部屋を同時に管理するための「下駄箱（Map）」
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
        
        // 📦 下駄箱から部屋一覧を取得
        Map<String, BingoGame> games = getGamesMap(application);
        
        // 🔍 現在のセッション（ブラウザ）がどの部屋IDに紐づいているかを追跡
        String sessionGameId = (String) session.getAttribute("myCurrentGameId");
        
        // ⏱️ 1. 定期自動期限チェック（動いているすべての部屋の有効期限を掃除）
        if (!games.isEmpty()) {
            games.entrySet().removeIf(entry -> {
                BingoGame g = entry.getValue();
                return g.isExpired() || g.isPast2HoursFromLastBingo();
            });
        }

        // 🚀 2. セッションからプレイヤー名を取得
        String confirmedName = (String) session.getAttribute("myConfirmedName");
        
        // 司会者でもなく、アクションもなく、入室もしていない場合はトップへ戻す
        if (action == null && !"admin".equals(userType) && sessionGameId == null) {
            response.sendRedirect("index.jsp");
            return;
        }

        // 🛠️ 3. 司会者(admin)向けの処理
        if ("admin".equals(userType)) {
            BingoGame game = null;
            if (sessionGameId != null) {
                game = games.get(sessionGameId);
            }

            if ("create".equals(action)) {
                // 新規作成時は、古い部屋の紐付けを一度クリアして新しく作る
                String validDaysParam = request.getParameter("validDays");
                int validDays = 8; // デフォルト値
                if (validDaysParam != null && !validDaysParam.isEmpty()) {
                    try {
                        validDays = Integer.parseInt(validDaysParam);
                    } catch (NumberFormatException e) {
                        validDays = 8;
                    }
                }
                
                // 被らない4桁の部屋番号(ID)を生成
                String newGameId;
                do {
                    newGameId = String.format("%04d", (int)(Math.random() * 10000));
                } while (games.containsKey(newGameId));
                
                game = new BingoGame(newGameId, validDays);
                games.put(newGameId, game); // 下駄箱に部屋を入れる
                
                sessionGameId = newGameId;
                session.setAttribute("myCurrentGameId", sessionGameId); // 司会者のセッションに部屋IDを記憶
                
            } else if ("draw".equals(action)) {
                if (game != null) {
                    List<Integer> pool = new ArrayList<>();
                    for (int i = 1; i <= 75; i++) {
                        if (!game.getDrawnNumbers().contains(i)) {
                            pool.add(i);
                        }
                    }
                    if (!pool.isEmpty()) {
                        Collections.shuffle(pool);
                        int drawn = pool.get(0);
                        game.getDrawnNumbers().add(drawn);
                        game.checkPlayersStatus(drawn); 
                    }
                }
            } else if ("reset".equals(action)) {
                if (sessionGameId != null) {
                    games.remove(sessionGameId); // 下駄箱からこの部屋を削除
                    session.removeAttribute("myCurrentGameId");
                    sessionGameId = null;
                    game = null;
                }
            }

            // 司会者画面(admin.jsp)へ、自分の担当する部屋データを載せてフォワード
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 👤 4. 一般プレイヤー向けの通常処理
        if ("join".equals(action)) {
            String inputGameId = request.getParameter("gameId");
            String inputName = request.getParameter("playerName");

            if (inputName == null || inputName.trim().isEmpty()) {
                request.setAttribute("error", "⚠️ 名前を入力してください。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
            inputName = inputName.trim();

            // 入力されたIDの部屋が下駄箱にあるか探す
            if (inputGameId != null && games.containsKey(inputGameId)) {
                BingoGame targetGame = games.get(inputGameId);
                
                if (confirmedName != null && !confirmedName.equals(inputName) && sessionGameId != null) {
                    BingoGame oldGame = games.get(sessionGameId);
                    if (oldGame != null) {
                        oldGame.getAllPlayers().remove(confirmedName);
                    }
                }
                
                if (!targetGame.getAllPlayers().contains(inputName)) {
                    targetGame.getAllPlayers().add(inputName);
                }
                
                confirmedName = inputName;
                sessionGameId = inputGameId;
                
                session.setAttribute("myConfirmedName", confirmedName);
                session.setAttribute("myCurrentGameId", sessionGameId); // プレイヤーのセッションに部屋IDを記憶
            } else {
                request.setAttribute("error", "⚠️ 部屋番号（ゲームID）が正しくありません、または有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        // 現在の部屋データを取得して同期
        BingoGame currentGame = null;
        if (sessionGameId != null) {
            currentGame = games.get(sessionGameId);
        }

        // 司会者がリセットして部屋が消えた、または数字が0個の場合は古いカードを破棄
        if (currentGame == null || currentGame.getDrawnNumbers().isEmpty()) {
            session.removeAttribute("card");
        }

        // 現在の有効なカードをセッションから取得してサーバー側に再同期
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
