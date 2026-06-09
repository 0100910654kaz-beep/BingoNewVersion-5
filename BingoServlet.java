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
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        // 🎤 3. 司会者コントロール画面のアクション処理
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
                        
                        // サーバー側の安全な新しい更新メソッド
                        game.updateAllPlayersStatus();
                    }
                } else if ("reset".equals(action)) {
                    // サーバー側の全データを一括クリア
                    game.clearGameDataOnly();
                }
            }
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 🎯 4. プレイヤー側のアクション処理
        if (game != null) {
            // 新規ログイン時
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

            // 🔄【ここが最重要修正！】
            // 自動更新（actionが空）の時であっても、司会者がリセット（数字が0個）したなら
            // プレイヤー全員の古いカード記憶（セッション）を確実にその場で破棄します！
            if (game.getDrawnNumbers().isEmpty()) {
                session.removeAttribute("card");
            }

            // 現在の有効なカードをセッションから取得してサーバー側に再同期
            List<List<String>> card = (List<List<String>>) session.getAttribute("card");
            if (card != null && confirmedName != null && !confirmedName.isEmpty()) {
                game.setPlayerCard(confirmedName, card);
            }
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
