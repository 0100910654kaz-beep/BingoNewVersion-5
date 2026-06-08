package servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");
        String userType = request.getParameter("userType"); 
        ServletContext application = getServletContext();
        HttpSession session = request.getSession();
        
        BingoGame game = (BingoGame) application.getAttribute("game");

        // ⏱️ 1. 定期自動期限チェック
        if (game != null) {
            if (game.isExpired() || game.isPast2HoursFromLastBingo()) {
                application.removeAttribute("game");
                game = null;
            }
        }

        // 🚀 2. セッションから認証済みの名前を確実に取得
        String confirmedName = (String) session.getAttribute("myConfirmedName");
        
        if (action == null && !"admin".equals(userType) && confirmedName == null) {
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        // 🎤 3. 司会者側のアクション処理
        if ("admin".equals(userType)) {
            if ("create".equals(action)) {
                String daysParam = request.getParameter("validDays");
                int days = 8; 
                if (daysParam != null && !daysParam.isEmpty()) {
                    try {
                        days = Integer.parseInt(daysParam);
                    } catch (NumberFormatException e) {
                        days = 8;
                    }
                }
                
                String gameId = String.format("%04d", (int)(Math.random() * 10000));
                game = new BingoGame(gameId, days);
                application.setAttribute("game", game);
                
                request.setAttribute("game", game);
                request.getRequestDispatcher("admin.jsp").forward(request, response);
                return;
            }
            
            if (game != null) {
                if ("draw".equals(action)) {
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
                        
                        // ⚡ 玉を引いたので全員のリーチ・ビンゴを再計算
                        game.checkAllPlayersStatus();
                    }
                } else if ("reset".equals(action)) {
                    game.getDrawnNumbers().clear();
                    game.getBingoPlayers().clear();
                    game.getReachPlayers().clear();
                }
            }
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 🎯 4. プレイヤーの処理（ログイン・自動更新共通の安全ルート）
        if (game != null) {
            // 司会者がログイン画面で「参加」ボタンを押した時
            if ("join".equals(action)) {
                String inputGameId = request.getParameter("gameId");
                String inputName = request.getParameter("playerName");

                if (inputName == null || inputName.trim().isEmpty()) {
                    inputName = game.generateAnonymousName();
                } else {
                    inputName = inputName.trim();
                }

                if (game.getGameId().equals(inputGameId)) {
                    if (game.getAllPlayers().contains(inputName) && !inputName.equals(confirmedName)) {
                        request.setAttribute("error", "⚠️ その名前はすでに使われています。");
                        request.getRequestDispatcher("index.jsp").forward(request, response);
                        return;
                    }
                    
                    if (!game.getAllPlayers().contains(inputName)) {
                        game.getAllPlayers().add(inputName);
                    }
                    
                    confirmedName = inputName;
                    session.setAttribute("myConfirmedName", confirmedName);
                } else {
                    request.setAttribute("error", "⚠️ 部屋番号（ゲームID）が正しくありません。");
                    request.getRequestDispatcher("index.jsp").forward(request, response);
                    return;
                }
            }

            // 🔄 司会者がリセット（数字が0個）した場合は、古いカードを強制破棄
            if (game.getDrawnNumbers().isEmpty()) {
                session.removeAttribute("card");
            }

            // セッション、またはサーバーから現在のカードを取得
            List<List<String>> card = (List<List<String>>) session.getAttribute("card");
            if (card == null && confirmedName != null && !confirmedName.isEmpty()) {
                card = game.getPlayerCard(confirmedName);
            }
            
            // 🎲 カードが存在しない、またはリセット直後なら新カードをランダムに配り直す
            if ((card == null || game.getDrawnNumbers().isEmpty()) && confirmedName != null && !confirmedName.isEmpty()) {
                List<List<Integer>> columns = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    List<Integer> pool = new ArrayList<>();
                    for (int j = 1; j <= 15; j++) { pool.add(i * 15 + j); }
                    Collections.shuffle(pool);
                    columns.add(pool.subList(0, 5));
                }
                
                card = new ArrayList<>();
                for (int r = 0; r < 5; r++) {
                    List<String> row = new ArrayList<>();
                    for (int c = 0; c < 5; c++) {
                        if (r == 2 && c == 2) { row.add("0"); }
                        else { row.add(String.valueOf(columns.get(c).get(r))); }
                    }
                    row.add(null); // オリジナルの構造を100%完全再現（6要素目）
                    card.add(row);
                }
                
                session.setAttribute("card", card);
                game.setPlayerCard(confirmedName, card);
                
                // 新カード配り直し時にもリーチ判定を全自動走査
                game.checkAllPlayersStatus();
            } else if (card != null) {
                session.setAttribute("card", card);
            }
        }

        // 🚚 5. プレイヤー画面（index.jsp）へ安全に出荷
        request.setAttribute("game", game);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.getRequestDispatcher("index.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}
