/*
 * Stores constants that will be used throughout the game.
 * Variable areas to include: Player, victory checking, end of game conditions.
 */
public interface OthelloConstants {
	//PLAYER VARS
	public static final int PLAYER1 = 1,
				PLAYER2 = 2;
	//VICTORY CONDITIONALS
	public static final int PLAYER1_WON = 1,
				PLAYER2_WON = 2,
				DRAW = 3,
				CONTINUE = 4,
				NOMOVE = 5;
	//END OF GAME CONDITIONALS
	public static final int PLAY_AGAIN = 1,
				EXIT = 0;
	
	//BOARD CONSTANTS
	public static final int BOARD_SIZE = 800;
	
	//TILE CONSTANTS
	public static final int TILE_SIZE = 100;
	
	//LAYOUT CONSTANTS
	public static final int WINDOW_HEIGHT = 875,
			WINDOW_WIDTH = 800;
	
	//SERVER CONSTANTS
	public static final int SERVER_SOCKET = 8000;
}
