// =================================================================
        // 👤 3. 一般プレイヤー向けの処理（完全にURL/パラメータ優先へ修正）
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
        
        // 【参加処理】
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
                
                if (confirmedName != null && !confirmedName.equals(inputName)) {
                    // 古い部屋からプレイヤーを削除
                    String oldGameId = (String) session.getAttribute("myCurrentGameId");
                    if (oldGameId != null) {
                        BingoGame oldGame = games.get(oldGameId);
                        if (oldGame != null) {
                            oldGame.getAllPlayers().remove(confirmedName);
                        }
                    }
                }
                
                if (!targetGame.getAllPlayers().contains(inputName)) {
                    targetGame.getAllPlayers().add(inputName);
                }
                
                confirmedName = inputName;
                targetGameId = inputGameId; // ターゲットIDを上書き
                
                session.setAttribute("myConfirmedName", confirmedName);
                session.setAttribute("myCurrentGameId", targetGameId); 
            } else {
                request.setAttribute("error", "⚠️ 指定された部屋番号（" + inputGameId + "）は存在しないか、有効期限切れです。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        // 確定した部屋IDからゲームオブジェクトを取得
        BingoGame currentGame = null;
        if (targetGameId != null && targetGameId.length() == 4) {
            currentGame = games.get(targetGameId);
        }

        // 部屋が見つからない場合の安全策
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

        // カードの同期
        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        if (card != null && confirmedName != null && !confirmedName.isEmpty()) {
            currentGame.setPlayerCard(confirmedName, card);
        }

        request.setAttribute("game", currentGame);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.getRequestDispatcher("index.jsp").forward(request, response);
    }
