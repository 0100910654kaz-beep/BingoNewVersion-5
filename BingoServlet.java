package servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/BingoServlet")
public class BingoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final ConcurrentHashMap<String, BingoGame> games = new ConcurrentHashMap<>();

    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        processRequest(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        HttpSession session = request.getSession(true);

        String action = request.getParameter("action");

        // 👑 司会者処理
        if ("createRoom".equals(action)) {
            String newGameId;
            synchronized (games) {
                do {
                    newGameId = String.format("%04d", (int)(Math.random() * 10000));
                } while (games.containsKey(newGameId));
                
                BingoGame newGame = new BingoGame(newGameId, 3);
                games.put(newGameId, newGame);
            }
            session.setAttribute("adminGameId", newGameId);
            response.sendRedirect("BingoServlet?action=adminPage&gameId=" + newGameId);
            return;
        }

        if ("adminPage".equals(action)) {
            String gameId = request.getParameter("gameId");
            if (gameId == null || gameId.isEmpty()) {
                gameId = (String) session.getAttribute("adminGameId");
            }
            
            BingoGame game = games.get(gameId);
            if (game == null) {
                request.setAttribute("error", "⚠️ 指定された部屋が存在しないか、有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
            
            session.setAttribute("adminGameId", gameId);
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        if ("draw".equals(action)) {
            String gameId = (String) session.getAttribute("adminGameId");
            BingoGame game = games.get(gameId);
            if (game != null) {
                game.drawNumber();
            }
            response.sendRedirect("BingoServlet?action=adminPage&gameId=" + gameId);
            return;
        }

        if ("resetGame".equals(action)) {
            String gameId = (String) session.getAttribute("adminGameId");
            BingoGame game = games.get(gameId);
            if (game != null) {
                game.resetGame();
            }
            response.sendRedirect("BingoServlet?action=adminPage&gameId=" + gameId);
            return;
        }

        // 👤 一般プレイヤー処理
        String targetGameId = request.getParameter("gameId");
        if (targetGameId == null || targetGameId.isEmpty()) {
            targetGameId = (String) session.getAttribute("myCurrentGameId");
        }

        // 🚨 部屋IDがどこにもない、またはサーバーに存在しない場合はセッションをクリアして完全に初期化
        if (targetGameId == null || targetGameId.isEmpty() || !games.containsKey(targetGameId)) {
            session.removeAttribute("card");
            session.removeAttribute("myConfirmedName");
            session.removeAttribute("myCurrentGameId");
            if (!"join".equals(action)) {
                request.setAttribute("error", "⚠️ 部屋の指定が正しくないか、有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        } else {
            session.setAttribute("myCurrentGameId", targetGameId);
        }

        BingoGame currentGame = games.get(targetGameId);
        if (currentGame == null) {
            session.removeAttribute("card");
            session.removeAttribute("myConfirmedName");
            session.removeAttribute("myCurrentGameId");
            request.setAttribute("error", "⚠️ お探しのビンゴ部屋が見つかりませんでした。");
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        // 🚨【連動リセットの核心ロジック】
        // 司会者がリセットした、あるいはサーバーが再起動して出た数字が0個かつプレイヤーが0人の場合、
        // プレイヤー側の古いセッション記憶を完全に抹殺し、強制的にID入力画面に戻す
        if (currentGame.getDrawnNumbers().isEmpty() && currentGame.getPlayerCount() == 0) {
            session.removeAttribute("card");
            session.removeAttribute("myConfirmedName");
            // もしjoin（ログイン試行）以外のアクセスであれば、入力をやり直させるためクリアして初期遷移
            if (!"join".equals(action)) {
                request.setAttribute("confirmedPlayerName", "");
                request.setAttribute("game", currentGame);
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        String confirmedName = (String) session.getAttribute("myConfirmedName");

        // 🚨 セッションに名前があるが、サーバー側の全プレイヤーリストに自分が含まれていない場合（司会者リセット後など）
        // セッションをクリアして再ログインを促す
        if (confirmedName != null && !confirmedName.isEmpty()) {
            if (!currentGame.getAllPlayers().contains(confirmedName)) {
                session.removeAttribute("card");
                session.removeAttribute("myConfirmedName");
                confirmedName = null;
            }
        }

        // 🚪 部屋に入る（join）ボタンを押した時の処理
        if ("join".equals(action)) {
            String inputName = request.getParameter("playerName");
            if (inputName != null) {
                inputName = inputName.trim();
            }

            if (inputName == null || inputName.isEmpty()) {
                request.setAttribute("error", "⚠️ お名前を入力してください。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }

            String uniqueName = inputName;
            
            // 同時アクセス競合防止の同期ブロック
            synchronized (currentGame) {
                if (currentGame.getPlayerCard(inputName) != null) {
                    int suffix = 1;
                    while (currentGame.getPlayerCard(inputName + suffix) != null) {
                        suffix++;
                    }
                    uniqueName = inputName + suffix;
                }
                
                // 仮の空カードを確保して名前をロック
                currentGame.setPlayerCard(uniqueName, new ArrayList<>());
            }

            session.setAttribute("myConfirmedName", uniqueName);
            confirmedName = uniqueName;
            session.removeAttribute("card");
        }

        if (confirmedName == null || confirmedName.isEmpty()) {
            String backupName = request.getParameter("playerName");
            if (backupName != null && !backupName.isEmpty()) {
                confirmedName = backupName.trim();
                session.setAttribute("myConfirmedName", confirmedName);
            }
        }

        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        
        if (card == null && confirmedName != null && !confirmedName.isEmpty()) {
            card = currentGame.getPlayerCard(confirmedName);
            if (card != null && card.isEmpty()) {
                card = null;
            }
            if (card != null) {
                session.setAttribute("card", card);
            }
        }
        
        if (card != null && confirmedName != null && !confirmedName.isEmpty()) {
            currentGame.setPlayerCard(confirmedName, card);
        }

        if (confirmedName == null) {
            confirmedName = request.getParameter("playerName");
        }
        
        request.setAttribute("game", currentGame);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.setAttribute("gameId", targetGameId);

        request.getRequestDispatcher("index.jsp").forward(request, response);
    }
}
