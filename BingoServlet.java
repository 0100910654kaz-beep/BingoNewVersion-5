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

        // 🚀 2. 【正しい交通整理】
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

        // 🎯 4. プレイヤーの「参加（ログイン）」処理
        if ("join".equals(action)) {
            String inputGameId = request.getParameter("gameId");
            String inputName = request.getParameter("playerName");

            if (inputName == null || inputName.trim().isEmpty()) {
                if (game != null) {
                    inputName = game.generateAnonymousName();
                } else {
                    inputName = "ゲスト";
                }
            } else {
                inputName = inputName.trim();
            }

            if (game != null && game.getGameId().equals(inputGameId)) {
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
                
                // 🔄 司会者がゲームをリセット（数字が0個）している場合は、古いカードを強制破棄
                if (game.getDrawnNumbers().isEmpty()) {
                    session.removeAttribute("card");
                }

                List<List<String>> card = game.getPlayerCard(confirmedName);
                
                // 🔄 サーバー側にカードがあっても、リセット状態なら新しいランダムカードを作成する
                if (card == null || game.getDrawnNumbers().isEmpty()) {
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
                        card.add(row);
                    }
                    
                    session.setAttribute("card", card);
                    game.setPlayerCard(confirmedName, card);
                } else {
                    session.setAttribute("card", card);
                }
                
                request.setAttribute("game", game);
                request.setAttribute("confirmedPlayerName", confirmedName);
                request.getRequestDispatcher("index.jsp").forward(request, response);
            } else {
                request.setAttribute("error", "⚠️ 部屋番号（ゲームID）が正しくありません。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
            }
            return;
        }

        // 🔄 5. その他のアクセス（5秒自動更新の受け皿）
        if (game != null && game.getDrawnNumbers().isEmpty()) {
            // 司会者がリセットした瞬間、全プレイヤーのカード記憶を消去して新カードを促す
            session.removeAttribute("card");
        }

        request.setAttribute("game", game);
        
        if (confirmedName == null) {
            confirmedName = request.getParameter("playerName");
        }
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.getRequestDispatcher("index.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}
