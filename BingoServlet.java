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
            // ランダムでユニークな4桁の部屋IDを生成
            String newGameId;
            synchronized (games) {
                do {
                    newGameId = String.format("%04d", (int)(Math.random() * 10000));
                } while (games.containsKey(newGameId));
                
                // 有効期間を3日としてゲームインスタンスを作成
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
                game.drawNumber(); // 抽選処理（内部で自動リーチ・ビンゴ判定を駆動）
            }
            response.sendRedirect("BingoServlet?action=adminPage&gameId=" + gameId);
            return;
        }

        if ("resetGame".equals(action)) {
            String gameId = (String) session.getAttribute("adminGameId");
            BingoGame game = games.get(gameId);
            if (game != null) {
                game.resetGame(); // 当選番号や当選者リストの完全初期化
            }
            response.sendRedirect("BingoServlet?action=adminPage&gameId=" + gameId);
            return;
        }

        // =================================================================
        // 👤 2. 一般プレイヤー向けの処理（完全にURL/パラメータ優先へ修正）
        // =================================================================
        
        // ⚡【超重要】セッションの記憶よりも、現在アクセスしようとしている「URLの部屋ID」を絶対最優先にする
        String targetGameId = request.getParameter("gameId");
        
        // もしURLになければ、フォーム(join時など)から取得を試みる
        if (targetGameId == null || targetGameId.isEmpty()) {
            targetGameId = (String) session.getAttribute("myCurrentGameId");
        }

        // 部屋IDが確定したらセッションを更新
        if (targetGameId != null && targetGameId.length() == 4 && games.containsKey(targetGameId)) {
            session.setAttribute("myCurrentGameId", targetGameId);
        } else {
            // 部屋IDがどこからも取得できない、または存在しない場合は初期画面へ
            if (!"join".equals(action)) {
                request.setAttribute("error", "⚠️ 部屋の指定が正しくないか、有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        String confirmedName = (String) session.getAttribute("myConfirmedName");
        
        // 🌟【★超重要ロジック】Renderがセッションを切断した際の自動救済
        // セッションから名前が消えていても、URLやリフレッシュのパラメータに名前が残っていれば執念深く復旧する！
        if (confirmedName == null || confirmedName.isEmpty()) {
            String backupName = request.getParameter("playerName");
            if (backupName != null && !backupName.isEmpty()) {
                confirmedName = backupName.trim();
                session.setAttribute("myConfirmedName", confirmedName);
            }
        }

        // 🚪 一般プレイヤーの「部屋に入る（join）」新規参加・復帰の受付処理
        if ("join".equals(action)) {
            String inputName = request.getParameter("playerName");
            String inputGameId = request.getParameter("gameId");

            if (inputGameId == null || inputGameId.length() != 4 || !games.containsKey(inputGameId)) {
                request.setAttribute("error", "⚠️ 正しい4桁の部屋番号を入力してください。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }

            if (inputName == null || inputName.trim().isEmpty()) {
                request.setAttribute("error", "⚠️ お名前を入力してください。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }

            inputName = inputName.trim();
            BingoGame targetGame = games.get(inputGameId);
            String uniqueName = inputName;

            // ⚡【同姓同名ナンバリング自動付与システム】
            // すでに同じ名前のプレイヤーがその部屋にカードを持っているか確認
            if (targetGame.getPlayerCard(inputName) != null) {
                int suffix = 1;
                // 「名前1」「名前2」「名前3」と順番に確認していき、まだ使われていない最小の空き番号を見つける
                while (targetGame.getPlayerCard(inputName + suffix) != null) {
                    suffix++;
                }
                // 空いていた一意の番号（例：佐藤1）を正式なプレイヤー名に決定
                uniqueName = inputName + suffix;
            }

            // セッション情報を固定
            session.setAttribute("myCurrentGameId", inputGameId);
            session.setAttribute("myConfirmedName", uniqueName);
            
            targetGameId = inputGameId;
            confirmedName = uniqueName;
        }

        // 現在アクセス中のビンゴゲームの取得
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
        }

        // 🌟【カードの同期とRender切断対策】
        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        
        // もしRenderにセッション（カード）を消されてしまっていたら、サーバー側（currentGame）から自分のカードを執念深く回収する
        if (card == null && confirmedName != null && !confirmedName.isEmpty()) {
            card = currentGame.getPlayerCard(confirmedName); // 👈 共通メモリから回収！
            if (card != null) {
                session.setAttribute("card", card); // セッションに復活させる
            }
        }
        
        // 逆にセッションにカードがあって、サーバー側に未登録なら同期する
        if (card != null && confirmedName != null && !confirmedName.isEmpty()) {
            currentGame.setPlayerCard(confirmedName, card);
        }

        // 最終的な安全チェック：もし名前がない状態ならindex.jsp側でバグるので、リクエストパラメータから最終補填
        if (confirmedName == null) {
            confirmedName = request.getParameter("playerName");
        }
        
        request.setAttribute("game", currentGame);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.setAttribute("gameId", targetGameId);

        request.getRequestDispatcher("index.jsp").forward(request, response);
    }
}
