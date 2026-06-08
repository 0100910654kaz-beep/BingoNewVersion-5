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
        // アクション（ボタン操作）がなく、司会者の自動更新でもなく、セッションに名前も入っていない
        // つまり【大山さんがURLを直接手で開いた時】だけ、確実に「日数設定の初期画面」を表示します。
        String confirmedName = (String) session.getAttribute("myConfirmedName");
        if ((action == null || action.trim().isEmpty()) && !"admin".equals(userType) && confirmedName == null) {
            request.setAttribute("game", null); 
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 🟢 3. 新しくゲーム（部屋）を作る処理
        if ("create".equals(action)) {
            String validDaysStr = request.getParameter("validDays");
            int validDays = 8; 
            if (validDaysStr != null) {
                try {
                    validDays = Integer.parseInt(validDaysStr);
                } catch (NumberFormatException e) {
                    validDays = 8;
                }
            }
            
            int random4Digit = (int)(Math.random() * 9000) + 1000;
            String newGameId = String.valueOf(random4Digit);
            
            game = new BingoGame(newGameId, validDays);
            application.setAttribute("game", game);
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 🔄 4. リセット処理
        if ("reset".equals(action)) {
            if (game != null) {
                game.clearGameDataOnly();
            }
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 🎲 5. 数字を引く処理
        if ("draw".equals(action)) {
            if (game != null) {
                game.drawNumber();
            }
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 👥 6. プレイヤーの参加処理
        if ("join".equals(action)) {
            String inputId = request.getParameter("gameId");
            String playerName = request.getParameter("playerName");
            
            if (game != null && game.getGameId().equals(inputId)) {
                
                // 💡 名前の空欄チェック。空っぽならJavaカウンターから「ゲストX」を取得する
                if (playerName == null || playerName.trim().isEmpty()) {
                    confirmedName = game.generateAnonymousName();
                } else {
                    confirmedName = playerName.trim();
                }
                
                List<List<String>> card = new ArrayList<>();
                List<List<Integer>> columns = new ArrayList<>();
                
                for (int i = 0; i < 5; i++) {
                    List<Integer> pool = new ArrayList<>();
                    for (int j = 1; j <= 15; j++) { pool.add(i * 15 + j); }
                    Collections.shuffle(pool);
                    columns.add(pool.subList(0, 5));
                }

                for (int r = 0; r < 5; r++) {
                    List<String> row = new ArrayList<>();
                    for (int c = 0; c < 5; c++) {
                        if (r == 2 && c == 2) { row.add("0"); } 
                        else { row.add(String.valueOf(columns.get(c).get(r))); }
                    }
                    card.add(row);
                }
                
                session.setAttribute("card", card);
                session.setAttribute("myConfirmedName", confirmedName);
                game.setPlayerCard(confirmedName, card);
                
                request.setAttribute("game", game);
                request.setAttribute("confirmedPlayerName", confirmedName);
                request.getRequestDispatcher("index.jsp").forward(request, response);
            } else {
                request.setAttribute("error", "⚠️ 部屋番号（ゲームID）が正しくありません。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
            }
            return;
        }

        // 7. その他のアクセス（5秒自動更新の受け皿）
        request.setAttribute("game", game);
        
        if ("admin".equals(userType)) {
            request.getRequestDispatcher("admin.jsp").forward(request, response);
        } else {
            if (confirmedName == null) {
                confirmedName = request.getParameter("playerName");
            }
            request.setAttribute("confirmedPlayerName", confirmedName);
            request.getRequestDispatcher("index.jsp").forward(request, response);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}
