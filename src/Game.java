import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.scene.Node;

import java.util.HashMap;

public class Game {

    public static int BOARD_SIZE = 8;
    public static int TILE_SIZE = 100;
    public static int PLAY_SQUARE = 0; //1 for white, 0 for black
    public static int AI_MOVE_LAG_TIME = 600; //milliseconds
    private static boolean RESET_GAME;
    public static boolean USER_MOVE_HIGHLIGHTING = true;
    public static boolean AI_MOVE_HIGHLIGHTING = true;
    private Player blackPlayer;
    private Player whitePlayer;
    private Board board;
    private final Group components;

    public Game(Player blackPlayer, Player whitePlayer) {
        components = new Group();
        this.blackPlayer = blackPlayer;
        this.whitePlayer = whitePlayer;
        resetGame();
        Platform.runLater(this::startNewGame);
    }

    private void startNewGame() {
        resetGame();
        GUI.output.setText(GUI.GAME_PREAMBLE_AND_INSTRUCTIONS);
        GUI.output.appendText("                       --- New game begins! ---\n");
        printNewTurnDialogue();
        RESET_GAME = false;
        nextPlayersTurn();
    }

    private void resetGame() {
        board = new Board();
        setAllUnitsLocked();
        addMouseControlToAllUnits();
        components.getChildren().setAll(board.getGUIComponents().getChildren());

        blackPlayer.resetPlayer();
        whitePlayer.resetPlayer();
    }

    public void restartGame(Player player) {
        if (getCurrentPlayer().isPlayerHuman()) {
            setPlayer(player);
            startNewGame();
        } else {
            setPlayer(player);
            RESET_GAME = true;
        }
    }

    public void toggleUserMoveHighlighting() {
        USER_MOVE_HIGHLIGHTING = !USER_MOVE_HIGHLIGHTING;
        refreshBoard();
    }

    private void setAllUnitsLocked() {
        board.getBlackUnits().setMouseTransparent(true);
        board.getWhiteUnits().setMouseTransparent(true);
    }

    private void nextPlayersTurn() {
        board.refreshTeamsAvailableMoves(getCurrentPlayer().getPlayerTeam());
        runNextMove();
    }

    private void runNextMove() {
        refreshBoard();
        if (isGameOver()) {
            Platform.runLater(() -> temporaryPause(5000));
            Platform.runLater(this::startNewGame);
        } else {
            processPlayerMove(getCurrentPlayer());
        }
    }

    private void temporaryPause(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void refreshBoard() {
        board.resetTileColors();
        if (getCurrentPlayer().isPlayerHuman()) {
            Platform.runLater(() -> {
                board.highlightUsersAvailableMoves();
                board.makeCurrentTeamAccessible(blackPlayer, whitePlayer);
            });
        } else {
            setAllUnitsLocked();
        }
    }

    private Player getCurrentPlayer() {
        if (blackPlayer.isPlayersTurn()) {
            return blackPlayer;
        } else return whitePlayer;
    }

    private boolean isGameOver() {
        if (board.getPossibleMoves().isEmpty()) {
            if (blackPlayer.isPlayersTurn()) {
                GUI.output.appendText("---------------------------------------------------\n");
                GUI.output.appendText("!!!!!!!!!!!!!!!!!!!  White player wins  !!!!!!!!!!!!!!!!!!\n");
                GUI.output.appendText("---------------------------------------------------\n");
                GUI.output.appendText("\nPlease stand by, a new game will start automatically soon in 3...2...1...\n");
                return true;
            } else {
                GUI.output.appendText("---------------------------------------------------\n");
                GUI.output.appendText("!!!!!!!!!!!!!!!!!!!  Black player wins   !!!!!!!!!!!!!!!!!!\n");
                GUI.output.appendText("---------------------------------------------------\n");
                GUI.output.appendText("\nPlease stand by, a new game will start automatically soon in 3...2...1...\n");
                return true;
            }
        }
        return RESET_GAME;
    }

    private void processPlayerMove(Player player) {
        Task<Void> task = new Task<Void>() {
            @Override
            public Void call() {
                temporaryPause(AI_MOVE_LAG_TIME);

                player.getPlayerMove(board).ifPresent(move -> {
                    board.highlightAIMove(move);
                    temporaryPause(AI_MOVE_LAG_TIME);
                    Platform.runLater(() -> executePlayerMove(move));
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    private void executePlayerMove(Move move) {
        boolean turnFinished = board.executeMove(move);
        if (turnFinished) {
            endTurn();
        } else {
            runNextMove();
        }
    }

    private void endTurn() {
        board.setUnitInMotion(null);
        switchPlayerTurn();
        printNewTurnDialogue();
        nextPlayersTurn();
    }

    private void switchPlayerTurn() {
        blackPlayer.switchTurn();
        whitePlayer.switchTurn();
        board.setNextPlayer();
    }

    private void printNewTurnDialogue() {
        String player;
        if (blackPlayer.isPlayersTurn()) {
            player = "Black";
        } else {
            player = "White";
        }
        GUI.output.appendText("---------------------------------------------------\n");
        GUI.output.appendText("                                  " + player + "'s turn\n");
    }

    public Group getComponents() {
        return components;
    }

    private void setPlayer(Player player) {
        if (player != null) {
            if (player.getPlayerTeam() == Team.BLACK) {
                blackPlayer = player;
            } else {
                whitePlayer = player;
            }
        }
    }

    private void addMouseControlToAllUnits() {
        for (Node node : board.getBlackUnits().getChildren()) {
            Unit unit = (Unit) node;
            addMouseControlToUnit(unit);
        }
        for (Node node : board.getWhiteUnits().getChildren()) {
            Unit unit = (Unit) node;
            addMouseControlToUnit(unit);
        }
    }

    private void addMouseControlToUnit(Unit unit) {
        unit.setOnMouseReleased(e -> {
            int targetX = Coordinates.toBoard(unit.getLayoutX());
            int targetY = Coordinates.toBoard(unit.getLayoutY());

            Coordinates origin = unit.getPos();
            Coordinates mouseDragTarget = new Coordinates(origin, targetX, targetY);

            programUnitNormalMode(unit, origin, mouseDragTarget);

        });
    }

    private void programUnitNormalMode(Unit unit, Coordinates origin, Coordinates mouseDragTarget) {
        Move actualMove = null;
        for (Move move : board.getPossibleMoves()) {
            if (move.getOrigin().equals(origin) && move.getTarget().equals(mouseDragTarget)) {
                actualMove = move;
                break;
            }
        }
        if (actualMove == null) {
            actualMove = new Move(unit.getPos(), mouseDragTarget, MoveType.NONE);
            actualMove.setInvalidMoveExplanation(getInvalidMoveError(actualMove));
        }
        executePlayerMove(actualMove);
    }

    private InvalidMoveError getInvalidMoveError(Move move) {
        Coordinates mouseDragTarget = move.getTarget();
        Coordinates origin = move.getOrigin();

        InvalidMoveError invalidMoveError;
        if (mouseDragTarget.isOutsideBoard()) {
            invalidMoveError = InvalidMoveError.OUTSIDE_BOARD_ERROR;
        } else if (origin.equals(mouseDragTarget)) {
            invalidMoveError = InvalidMoveError.SAME_POSITION_ERROR;
        } else if (board.isOccupiedTile(mouseDragTarget)) {
            invalidMoveError = InvalidMoveError.TILE_ALREADY_OCCUPIED_ERROR;
        } else if (!mouseDragTarget.isPlaySquare()) {
            invalidMoveError = InvalidMoveError.NOT_PLAY_SQUARE_ERROR;
        } else {
            invalidMoveError = InvalidMoveError.DISTANT_MOVE_ERROR;
        }
        return invalidMoveError;
    }

    public void saveGame() {
        HashMap<Coordinates, UnitData> saveData = new HashMap<>();


    }

}