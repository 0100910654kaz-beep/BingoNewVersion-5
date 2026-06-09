package servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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

    // 全てのビンゴ部屋を管理する共通メモリスペース（サーバー起動中、永続保持）
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

        // =================================================================
        // 👑 1. 司会者（管理者）向けの処理
        // =================================================================
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

        // =================================================================
        // 👤 2. 一般プレイヤー向けの処理（重複回避ロジックを最優先に強化）
        // =================================================================
        
        String targetGameId = request.getParameter("gameId");
        if (targetGameId == null || targetGameId.isEmpty()) {
            targetGameId = (String) session.getAttribute("myCurrentGameId");
        }

        if (targetGameId != null && targetGameId.length() == 4 && games.containsKey(targetGameId)) {
            session.setAttribute("myCurrentGameId", targetGameId);
        } else {
            if (!"join".equals(action)) {
                request.setAttribute("error", "⚠️ 部屋の指定が正しくないか、有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        BingoGame currentGame = games.get(targetGameId);
        if (currentGame == null) {
            session.removeAttribute("card");
            request.setAttribute("error", "⚠️ お探しのビンゴ部屋が見つかりませんでした。");
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        // 司会者がリセットして数字が0個の場合は古いカードを破棄
        if (currentGame.getDrawnNumbers().isEmpty()) {
            session.removeAttribute("card");
            session.removeAttribute("myConfirmedName");
        }

        String confirmedName = (String) session.getAttribute("myConfirmedName");

        // 🚪 【参加ボタン（join）を押した時の処理】ここを最優先かつ厳格に判定！
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

            // 新しくボタンを押して入ってきた場合、現在のセッション情報に関わらず名前の重複チェックをかける
            String uniqueName = inputName;
            
            // サーバーの共通メモリにすでにその名前が存在するかを厳格にチェック
            if (currentGame.getPlayerCard(inputName) != null) {
                int suffix = 1;
                // 空いている最小の番号（佐藤1, 佐藤2...）が見つかるまでループ
                while (currentGame.getPlayerCard(inputName + suffix) != null) {
                    suffix++;
                }
                uniqueName = inputName + suffix;
            }

            // 確定した唯一無二の名前をセッションと変数に焼き付ける
            session.setAttribute("myConfirmedName", uniqueName);
            confirmedName = uniqueName;
            
            // 新規参加なので、古い端末のカード記憶が残っていれば一旦消去して新しく作らせる
            session.removeAttribute("card");
        }

        // 🌟【Render切断からの復旧ロジック】ボタンを押していないリフレッシュ時などの救済
        if (confirmedName == null || confirmedName.isEmpty()) {
            String backupName = request.getParameter("playerName");
            if (backupName != null && !backupName.isEmpty()) {
                confirmedName = backupName.trim();
                session.setAttribute("myConfirmedName", confirmedName);
            }
        }

        // 🌟【カードの同期処理】
        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        
        // セッションから消えていたらサーバー共通メモリから執念深く回収
        if (card == null && confirmedName != null && !confirmedName.isEmpty()) {
            card = currentGame.getPlayerCard(confirmedName);
            if (card != null) {
                session.setAttribute("card", card);
            }
        }
        
        // サーバー側に未登録なら同期する
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
