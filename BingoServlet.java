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
        String urlGameId = request.getParameter("gameId"); // 🌐 URLから部屋IDを直接監視
        ServletContext application = getServletContext();
        HttpSession session = request.getSession();
        
        Map<String, BingoGame> games = getGamesMap(application);
        
        // ⏱️ 1. 定期自動期限チェック
        if (!games.isEmpty()) {
            games.entrySet().removeIf(entry -> {
                BingoGame g = entry.getValue();
                return g.isExpired() || g.isPast2HoursFromLastBingo();
            });
        }

        String confirmedName = (String) session.getAttribute("myConfirmedName");
        String sessionGameId = null;

        // 🛠️ 2. 司会者(admin)向けの完全独立処理
        if ("admin".equals(userType)) {
            // ⭐【最重要：上書き改悪の修正壁】
            // URLに有効な4桁のgameIdが含まれている場合のみ、その部屋を管理対象とする。
            // 大山専用リンクのようにURLに部屋IDが含まれていない場合は、セッションの古い記憶を引き継がず、
            // 完全に空っぽの状態（新規作成待ち状態）としてスタートさせることで、別タブでの部屋混ざりを100%防ぎます。
            if (urlGameId != null && urlGameId.length() == 4 && games.containsKey(urlGameId)) {
                sessionGameId = urlGameId;
            }

            BingoGame currentGame = null;
            if (sessionGameId != null) {
                currentGame = games.get(sessionGameId);
            }

            if ("create".equals(action)) {
                String validDaysParam = request.getParameter("validDays");
                int validDays = 8; 
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
                
                currentGame = new BingoGame(newGameId, validDays);
                games.put(newGameId, currentGame);
                sessionGameId = newGameId;
                
            } else if ("draw".equals(action)) {
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
                        currentGame.checkPlayersStatus(drawn); 
                    }
                }
            } else if ("reset".equals(action)) {
                if (sessionGameId != null) {
                    games.remove(sessionGameId);
                    sessionGameId = null;
                    currentGame = null;
                }
            }

            request.setAttribute("game", currentGame);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 👤 3. 一般プレイヤー向けの完全独立処理
        // プレイヤーの場合は、URLパラメータ、またはセッションから部屋IDを特定
        if (urlGameId != null && urlGameId.length() == 4 && games.containsKey(urlGameId)) {
            sessionGameId = urlGameId;
        } else {
            sessionGameId = (String) session.getAttribute("myCurrentGameId");
        }

        if (action == null && sessionGameId == null) {
            response.sendRedirect("index.jsp");
            return;
        }

        if ("join".equals(action)) {
            String inputGameId = request.getParameter("gameId");
            String inputName = request.getParameter("playerName");

            if (inputName == null || inputName.trim().isEmpty()) {
                request.setAttribute("error", "⚠️ 名前を入力してください。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
            inputName = inputName.trim();

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
                session.setAttribute("myCurrentGameId", sessionGameId); 
            } else {
                request.setAttribute("error", "⚠️ 部屋番号（ゲームID）が正しくありません、または有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        BingoGame currentGame = null;
        if (sessionGameId != null && sessionGameId.length() == 4) {
            currentGame = games.get(sessionGameId);
        }

        if (currentGame == null || currentGame.getDrawnNumbers().isEmpty()) {
            session.removeAttribute("card");
        }

        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        if (card != null && confirmedName != null && !confirmedName.isEmpty() && currentGame != null) {
            currentGame.setPlayerCard(confirmedName, card);
        }

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
