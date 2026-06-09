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

        // 🚀 2. セッションからプレイヤー名を確実に取得
        String confirmedName = (String) session.getAttribute("myConfirmedName");
        
        if (action == null && !"admin".equals(userType) && confirmedName == null) {
            response.sendRedirect("index.jsp");
            return;
        }

        // 🛠️ 3. 司会者(admin)向けの処理
        if ("admin".equals(userType)) {
            if ("create".equals(action)) {
                if (game == null) {
                    String validDaysParam = request.getParameter("validDays");
                    int validDays = 8; // デフォルト値
                    if (validDaysParam != null && !validDaysParam.isEmpty()) {
                        try {
                            validDays = Integer.parseInt(validDaysParam);
                        } catch (NumberFormatException e) {
                            validDays = 8;
                        }
                    }
                    
                    String newGameId = String.format("%04d", (int)(Math.random() * 10000));
                    game = new BingoGame(newGameId, validDays);
                    application.setAttribute("game", game);
                }
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
                application.removeAttribute("game");
                game = null;
            }

            // ⚡【真のバグ修正！】
            // 5秒ごとの自動更新（actionが空）でアクセスしてきた場合も含め、
            // 司会者(admin)からのアクセスであれば、確実に「admin.jsp」へフォワードさせます。
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

            if (game != null && game.getGameId().equals(inputGameId)) {
                if (confirmedName != null && !confirmedName.equals(inputName)) {
                    game.getAllPlayers().remove(confirmedName);
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

        // 🔄 司会者がリセット（数字が0個）した場合は古いカードをセッションから即時破棄
        if (game != null && game.getDrawnNumbers().isEmpty()) {
            session.removeAttribute("card");
        }

        // ⚡ 現在の有効なカードをセッションから取得してサーバー側（BingoGame）に再同期
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        if (card != null && confirmedName != null && !confirmedName.isEmpty() && game != null) {
            game.setPlayerCard(confirmedName, card);
        }

        // 🚚 5. プレイヤー画面（index.jsp）に必要なオブジェクトを載せてフォワード
        if (confirmedName == null) {
            confirmedName = request.getParameter("playerName");
        }
        request.setAttribute("game", game);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.getRequestDispatcher("index.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}
