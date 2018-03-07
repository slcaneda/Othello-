import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.LineBorder;

/*
 * Holds each client that will be able to access the game.
 */
public class OthelloClient extends JApplet implements Runnable, OthelloConstants{
	
	//SERVER Vars
	private DataInputStream fromServer;
	private DataOutputStream toServer;
	private String host = "localhost";
	private int sendRow,
		sendCol;
	
	//LAYOUT vars
	private JTextArea log;
	private JLabel scoreLog;
	
	//GAME BOARD VARS
	private GameTile[][] board = new GameTile[8][8];
	
	//PLAYER vars
	int thisPlayer;
	String thisPlayerColor,
		otherPlayerColor,
		thisPlayerHighlight;
	boolean thisPlayerTurn = false;
	boolean stillPlaying = true,
			playerWaiting = true;
	
	//MAIN
	public static void main(String[] args){
		//first create the game window
		JFrame window = new JFrame("Reversi Client");

		window.setSize(OthelloConstants.WINDOW_WIDTH, OthelloConstants.WINDOW_HEIGHT);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
		
		//create the applet that the game will run on, init and start it
		OthelloClient applet = new OthelloClient();
		if(args.length == 1)
			applet.host = args[0];
		applet.init();
		applet.start();
		
		//add the applet to the jframe and revalidate
		window.setResizable(false);
		window.getContentPane().add(applet, BorderLayout.CENTER);
		window.revalidate();
	}
	
	/*
	 * Method used to output any important info to the log
	 */
	private void printOutput(String output){
		Date date = new Date();
		log.append(date.toString() + ": " + output + "\n");
	}
	/*
	 * Initializes the game by setting up its UI and connecting to the server.
	 */
	public void init(){
		//create the score log and set its preferred size
		scoreLog = new JLabel();
		scoreLog.setFont(scoreLog.getFont().deriveFont(16f));
		scoreLog.setPreferredSize(new Dimension(OthelloConstants.WINDOW_WIDTH, 25));
		
		//create the game board panel that is made of a grid layout
		JPanel boardPanel = new JPanel();
		boardPanel.setBackground(Color.black);
		boardPanel.setBorder(new LineBorder(Color.black, 2));
		boardPanel.setLayout(new GridLayout(8, 8));
		//create each tile
		for(int i = 0; i < 8; i++){
			for(int j = 0; j < 8; j++){
				boardPanel.add(board[i][j] = new GameTile(i, j));
			}
		}
		boardPanel.setPreferredSize(new Dimension(800, 800));
		
		//create the text area and scroll pane for server communications
		log = new JTextArea();
		log.setEditable(false);
		log.setFont(log.getFont().deriveFont(16f));
		JScrollPane scrollPane = new JScrollPane(log);
		scrollPane.setBorder(new LineBorder(Color.black, 2));
		scrollPane.setPreferredSize(new Dimension(OthelloConstants.WINDOW_WIDTH, 50));
		
		//add each component to the applet
		getContentPane().add(scoreLog, BorderLayout.NORTH);
		getContentPane().add(boardPanel, BorderLayout.CENTER);
		getContentPane().add(scrollPane, BorderLayout.SOUTH);
		scoreLog.setText("Player 1 score:        Player 2 score: ");
				
		//set starting pieces
		board[3][3].setPiece(GameTile.BLACK);
		board[3][3].repaint();
		board[3][4].setPiece(GameTile.WHITE);
		board[3][4].repaint();
		board[4][3].setPiece(GameTile.WHITE);
		board[4][3].repaint();
		board[4][4].setPiece(GameTile.BLACK);
		board[4][4].repaint();
		
		connectToServer();
	}
	
	/*
	 * Establishes a connection to the server and starts the game on a new separate
	 * thread.
	 */
	private void connectToServer(){
		//attempt to connect to the server
		try{
			//connect to the server using a socket
			Socket socket;
			socket = new Socket(host, 8000);
			
			//create the input and output stream with the server
			fromServer = new DataInputStream(socket.getInputStream());
			toServer = new DataOutputStream(socket.getOutputStream());
		}catch(Exception e){
			System.out.println("An Error occurred while connecting to the server.");
		}
		
		//Create the game thread
		Thread thread = new Thread(this);
		thread.start();
	}
	
	/*
	 * Method that runs the continuous game thread.
	 */
	public void run() {
		//try to run the game
		try{
			//get the player info from the server
			thisPlayer = fromServer.readInt();
			
			//update the game stats depending on which player this is.
			if(thisPlayer == PLAYER1){
				//set player 1 pieces
				thisPlayerColor = GameTile.BLACK;
				otherPlayerColor = GameTile.WHITE;
				thisPlayerHighlight = GameTile.HIGHLIGHT_BLACK;
				printOutput("You are player 1 with black game pieces.");
				
				//wait for response from server that both players are connected.
				fromServer.readInt();
				
				//now we know player 2 has joined, report this to the player
				printOutput("Player 2 has joined with white game pieces. You go first, so choose a square.");
				thisPlayerTurn = true;
			}else if(thisPlayer == PLAYER2){
				//set player 2 pieces
				thisPlayerColor = GameTile.WHITE;
				otherPlayerColor = GameTile.BLACK;
				thisPlayerHighlight = GameTile.HIGHLIGHT_WHITE;
				printOutput("You are player 2 with white game pieces.");
				printOutput("It is player 1's turn, they are placing black pieces.");
			}
			
			updateScore();
			
			//play the game
			while(stillPlaying){
				if(thisPlayer == PLAYER1){
					waitForPlayerClick();
					sendMove();
					receiveInfo();
				}else if(thisPlayer == PLAYER2){
					receiveInfo();
					waitForPlayerClick();
					sendMove();
				}
			}
		}catch(Exception e){
			System.out.println("An Error occured when trying to play the game.");
			e.printStackTrace();
		}
	}
	
	/*
	 * This method will be called to wait for the current player to
	 * make a move by clicking an appropriate tile.
	 */
	private void waitForPlayerClick() throws InterruptedException{
		thisPlayerTurn = true;
		highlightBoard();
		while(playerWaiting){
			Thread.sleep(100);
		}
		
		playerWaiting = true;
		clearHighlight();
		updateBoard(sendRow, sendCol, thisPlayerColor);
	}
	
	/*
	 * This method will be called to send a move to the server.
	 */
	private void sendMove() throws IOException{
		toServer.writeInt(sendRow);
		toServer.writeInt(sendCol);		
	}
	
	/*
	 * This method will be called to wait for this player to 
	 * receive an action from the server.
	 */
	private void receiveInfo() throws IOException{
		/*
		 * Remember to check the order of received info as it 
		 * is different than the example provided because the 
		 * order was changed in the server. 
		 */
		int receiveRow = fromServer.readInt();
		int receiveCol = fromServer.readInt();
		//place the piece
		printOutput("The other player placed a piece at row " + (receiveRow + 1) + " column " + (receiveCol + 1));
		board[receiveRow][receiveCol].setPiece(otherPlayerColor);
		board[receiveRow][receiveCol].repaint();
		updateBoard(receiveRow, receiveCol, otherPlayerColor);
		
		int status = fromServer.readInt();
		
		if(status == PLAYER1_WON){
			stillPlaying = false;
			if(thisPlayer == PLAYER1){
				printOutput("You Win! :)");
			}else if(thisPlayer == PLAYER2){
				printOutput("You Lose! :(");
			}
			
		}else if(status == PLAYER2_WON){
			stillPlaying = false;
			if(thisPlayer == PLAYER1){
				printOutput("You Lose! :(");
			}else if(thisPlayer == PLAYER2){
				printOutput("You Win! :)");
			}
			
		}else if(status == DRAW){
			stillPlaying = false;
			printOutput("Game ended in a draw!");
			
		}else if(status == CONTINUE){
			
			printOutput("Your turn!");
			thisPlayerTurn = true;
			
		}else if(status == NOMOVE){
			printOutput("You cannot make a move this turn. Other player's turn!");
		}
	}
	
	/*
	 * This method will highlight the game pieces that can be selected
	 * by this player.
	 */
	private void highlightBoard(){
		//for each tile
		for(int i = 0; i < board.length; i++){
			for(int j = 0; j < board[0].length; j++){
				boolean needsHighlight = false;
				if(board[i][j].gamePiece == GameTile.NONE){
					
					//check tiles above
					if(i > 0){
						//if the next piece is the other player, make sure there is a this player color in the line
						if(board[i - 1][j].gamePiece == otherPlayerColor){
							//check every piece until none or opposite color hit
							int tileRow = i - 1;
							while(tileRow > 0){
								if(board[tileRow][j].gamePiece == GameTile.NONE){
									break;
								}else if(board[tileRow][j].gamePiece == thisPlayerColor){
									needsHighlight = true;
								}
								tileRow--;
							}
						}
					}
					
					//check tiles below
					if(i < board.length - 1){
						//if the next piece is the other player, make sure there is a this player color in the line
						if(board[i + 1][j].gamePiece == otherPlayerColor){
							//check every piece until none or opposite color hit
							int tileRow = i + 1;
							while(tileRow < board.length){
								if(board[tileRow][j].gamePiece == GameTile.NONE){
									break;
								}else if(board[tileRow][j].gamePiece == thisPlayerColor){
									needsHighlight = true;
								}
								tileRow++;
							}
						}
					}
				
					//check tiles left
					if(j > 0){
						//if the next piece is the other player, make sure there is a this player color in the line
						if(board[i][j - 1].gamePiece == otherPlayerColor){
							//check every piece until none or opposite color hit
							int tileCol = j - 1;
							while(tileCol > 0){
								if(board[i][tileCol].gamePiece == GameTile.NONE){
									break;
								}else if(board[i][tileCol].gamePiece == thisPlayerColor){
									needsHighlight = true;
								}
								tileCol--;
							}
						}
					}
				
					//check tiles right
					if(j < board[0].length - 1){
						//if the next piece is the other player, make sure there is a this player color in the line
						if(board[i][j + 1].gamePiece == otherPlayerColor){
							//check every piece until none or opposite color hit
							int tileCol = j + 1;
							while(tileCol < board[0].length){
								if(board[i][tileCol].gamePiece == GameTile.NONE){
									break;
								}else if(board[i][tileCol].gamePiece == thisPlayerColor){
									needsHighlight = true;
								}
								tileCol++;
							}
						}
					}
				
					//check tiles top-left diagonal
					if(i > 0 && j > 0){
						//if the next piece is the other player, make sure there is a this player color in the line
						if(board[i - 1][j - 1].gamePiece == otherPlayerColor){
							//check every piece until none or opposite color hit
							int tileCol = j - 1;
							int tileRow = i - 1;
							while(tileCol > 0 && tileRow > 0){
								if(board[tileRow][tileCol].gamePiece == GameTile.NONE){
									break;
								}else if(board[tileRow][tileCol].gamePiece == thisPlayerColor){
									needsHighlight = true;
								}
								tileCol--;
								tileRow--;
							}
						}
					}
				
					//check tiles top_right diagonal
					if(i > 0 && j < board[0].length - 1){
						//if the next piece is the other player, make sure there is a this player color in the line
						if(board[i - 1][j + 1].gamePiece == otherPlayerColor){
							//check every piece until none or opposite color hit
							int tileCol = j + 1;
							int tileRow = i - 1;
							while(tileRow > 0 && tileCol < board[0].length){
								if(board[tileRow][tileCol].gamePiece == GameTile.NONE){
									break;
								}else if(board[tileRow][tileCol].gamePiece == thisPlayerColor){
									needsHighlight = true;
								}
								tileCol++;
								tileRow--;
							}
						}
					}
				
					//check tiles bottom_right diagonal
					if(i < board.length - 1 && j < board[0].length - 1){
						//if the next piece is the other player, make sure there is a this player color in the line
						if(board[i + 1][j + 1].gamePiece == otherPlayerColor){
							//check every piece until none or opposite color hit
							int tileCol = j + 1;
							int tileRow = i + 1;
							while(tileRow < board.length && tileCol < board[0].length){
								if(board[tileRow][tileCol].gamePiece == GameTile.NONE){
									break;
								}else if(board[tileRow][tileCol].gamePiece == thisPlayerColor){
									needsHighlight = true;
								}
								tileCol++;
								tileRow++;
							}
						}
					}
				
					//check tiles bottom_left diagonal
					if(i < board.length - 1 && j > 0){
						//if the next piece is the other player, make sure there is a this player color in the line
						if(board[i + 1][j - 1].gamePiece == otherPlayerColor){
							//check every piece until none or opposite color hit
							int tileCol = j - 1;
							int tileRow = i + 1;
							while(tileRow < board.length && tileCol > 0){
								if(board[tileRow][tileCol].gamePiece == GameTile.NONE){
									break;
								}else if(board[tileRow][tileCol].gamePiece == thisPlayerColor){
									needsHighlight = true;
								}
								tileCol--;
								tileRow++;
							}
						}
					}
				}
				
				//if needsHighlight is true, highlight this tile
				if(needsHighlight){
					board[i][j].gamePiece = thisPlayerHighlight;
					board[i][j].repaint();
				}
			}
		}
	}
	
	/*
	 * This method will fix the board by clearing all highlighted
	 * tiles.
	 */
	private void clearHighlight(){
		for(int i = 0; i < board.length; i++){
			for(int j = 0; j < board[0].length; j++){
				if(board[i][j].gamePiece == GameTile.HIGHLIGHT_BLACK || board[i][j].gamePiece == GameTile.HIGHLIGHT_WHITE){
					board[i][j].gamePiece = GameTile.NONE;
					board[i][j].repaint();
				}
			}
		}
	}
	
	/*
	 * Update board method is called after a new piece is added and will
	 * change the color of any pieces that should be changed. Works by using
	 * the passed in coords and checking which pieces need to be updated.
	 */
	private void updateBoard(int newRow, int newCol, String color){
		String thisColor = color;
		
		boolean hasSameColorPiece = false,
				hasGaps = false;
		int sameColorRow = 0,
			sameColorCol = 0;
		
		//check pieces above this one
		//first find the same color piece (if exists) and check for blank spots
		if(newRow > 0){
			for(int i = newRow - 1; i >= 0; i--){
				if(board[i][newCol].gamePiece == thisColor){
					hasSameColorPiece = true;
					sameColorRow = i;
					sameColorCol = newCol;
					break;
				}
			}
			
			if(hasSameColorPiece){
				for(int i = newRow; i > sameColorRow; i--){
					if(board[i][newCol].gamePiece == GameTile.NONE){
						hasGaps = true;
						break;
					}
				}
			}
			
			//if no blank spots, convert every piece between the two
			if(!hasGaps && hasSameColorPiece){
				for(int i = newRow; i > sameColorRow; i--){
					board[i][newCol].gamePiece = thisColor;
					board[i][newCol].repaint();
				}
			}
		}
		
		//below
		hasSameColorPiece = false;
		hasGaps = false;
		
		if(newRow < board.length - 1){
			for(int i = newRow + 1; i <= board.length - 1; i++){
				if(board[i][newCol].gamePiece == thisColor){
					hasSameColorPiece = true;
					sameColorRow = i;
					sameColorCol = newCol;
					break;
				}
			}
			
			if(hasSameColorPiece){
				for(int i = newRow; i < sameColorRow; i++){
					if(board[i][newCol].gamePiece == GameTile.NONE){
						hasGaps = true;
						break;
					}
				}
			}
			
			//if no blank spots, convert every piece between the two
			if(!hasGaps && hasSameColorPiece){
				for(int i = newRow; i < sameColorRow; i++){
					board[i][newCol].gamePiece = thisColor;
					board[i][newCol].repaint();
				}
			}
		}
		
		//right
		hasSameColorPiece = false;
		hasGaps = false;
		
		if(newCol < board.length - 1){
			for(int i = newCol + 1; i <= board.length - 1; i++){
				if(board[newRow][i].gamePiece == thisColor){
					hasSameColorPiece = true;
					sameColorRow = newRow;
					sameColorCol = i;
					break;
				}
			}
			
			if(hasSameColorPiece){
				for(int i = newCol; i < sameColorCol; i++){
					if(board[newRow][i].gamePiece == GameTile.NONE){
						hasGaps = true;
						break;
					}
				}
			}
			
			//if no blank spots, convert every piece between the two
			if(!hasGaps && hasSameColorPiece){
				for(int i = newCol; i < sameColorCol; i++){
					board[newRow][i].gamePiece = thisColor;
					board[newRow][i].repaint();
				}
			}
		}
		
		//left
		hasSameColorPiece = false;
		hasGaps = false;
		
		if(newCol > 0){
			for(int i = newCol - 1; i >= 0; i--){
				if(board[newRow][i].gamePiece == thisColor){
					hasSameColorPiece = true;
					sameColorRow = newRow;
					sameColorCol = i;
					break;
				}
			}
			
			if(hasSameColorPiece){
				for(int i = newCol; i > sameColorCol; i--){
					if(board[newRow][i].gamePiece == GameTile.NONE){
						hasGaps = true;
						break;
					}
				}
			}
			
			//if no blank spots, convert every piece between the two
			if(!hasGaps && hasSameColorPiece){
				for(int i = newCol; i > sameColorCol; i--){
					board[newRow][i].gamePiece = thisColor;
					board[newRow][i].repaint();
				}
			}
		}
		
		//top-left diag
		hasSameColorPiece = false;
		hasGaps = false;
		
		if(newCol > 0 && newRow > 0){
			for(int i = newRow - 1, j = newCol - 1; i >= 0 && j >= 0; i--, j-- ){
				if(board[i][j].gamePiece == thisColor){
					hasSameColorPiece = true;
					sameColorRow = i;
					sameColorCol = j;
					break;
				}
			}
			
			if(hasSameColorPiece){
				for(int i = newRow, j = newCol; i > sameColorRow && j > sameColorCol; i--, j--){
					if(board[i][j].gamePiece == GameTile.NONE){
						hasGaps = true;
						break;
					}
				}
			}
			
			if(!hasGaps && hasSameColorPiece){
				for(int i = newRow, j = newCol; i > sameColorRow && j > sameColorCol; i--, j--){
					board[i][j].gamePiece = thisColor;
					board[i][j].repaint();
				}
			}
		}
		
		//top-right diag
		hasSameColorPiece = false;
		hasGaps = false;
		
		if(newCol < board.length - 1 && newRow > 0){
			for(int i = newRow - 1, j = newCol + 1; i >= 0 && j <= board.length - 1; i--, j++){
				if(board[i][j].gamePiece == thisColor){
					hasSameColorPiece = true;
					sameColorRow = i;
					sameColorCol = j;
					break;
				}
			}
			
			if(hasSameColorPiece){
				for(int i = newRow, j = newCol; i > sameColorRow && j < sameColorCol; i--, j++){
					if(board[i][j].gamePiece == GameTile.NONE){
						hasGaps = true;
						break;
					}
				}
			}
			
			if(!hasGaps && hasSameColorPiece){
				for(int i = newRow, j = newCol; i > sameColorRow && j < sameColorCol; i--, j++){
					board[i][j].gamePiece = thisColor;
					board[i][j].repaint();
				}
			}
		}
		
		//bottom-right diag
		hasSameColorPiece = false;
		hasGaps = false;
		
		if(newCol < board.length - 1 && newRow < board.length - 1){
			for(int i = newRow + 1, j = newCol + 1; i <= board.length - 1 && j <= board.length - 1; i++, j++){
				if(board[i][j].gamePiece == thisColor){
					hasSameColorPiece = true;
					sameColorRow = i;
					sameColorCol = j;
					break;
				}
			}
			
			if(hasSameColorPiece){
				for(int i = newRow, j = newCol; i < sameColorRow && j < sameColorCol; i++, j++){
					if(board[i][j].gamePiece == GameTile.NONE){
						hasGaps = true;
						break;
					}
				}
			}
			
			if(!hasGaps && hasSameColorPiece){
				for(int i = newRow, j = newCol; i < sameColorRow && j < sameColorCol; i++, j++){
					board[i][j].gamePiece = thisColor;
					board[i][j].repaint();
				}
			}
		}
		
		//bottom-left diag
		hasSameColorPiece = false;
		hasGaps = false;
		
		if(newCol > 0 && newRow < board.length - 1){
			for(int i = newRow + 1, j = newCol - 1; i <= board.length - 1 && j >= 0; i++, j--){
				if(board[i][j].gamePiece == thisColor){
					hasSameColorPiece = true;
					sameColorRow = i;
					sameColorCol = j;
					break;
				}
			}
			
			if(hasSameColorPiece){
				for(int i = newRow, j = newCol; i < sameColorRow && j > sameColorCol; i++, j--){
					if(board[i][j].gamePiece == GameTile.NONE){
						hasGaps = true;
						break;
					}
				}
			}
			
			if(!hasGaps && hasSameColorPiece){
				for(int i = newRow, j = newCol; i < sameColorRow && j > sameColorCol; i++, j--){
					board[i][j].gamePiece = thisColor;
					board[i][j].repaint();
				}
			}
		}
		updateScore();
	}
	
	/*
	 * Updates the score that appears in the label on the top of the game.
	 */
	private void updateScore(){
		int player1Score = 0,
			player2Score = 0;
		
		for(int i = 0; i < board.length; i++){
			for(int j = 0; j < board[0].length; j++){
				if(board[i][j].gamePiece == GameTile.BLACK){
					player1Score++;
				}else if(board[i][j].gamePiece == GameTile.WHITE){
					player2Score++;
				}
			}
		}
		
		scoreLog.setText("Player 1 score: " + player1Score + "       Player 2 score: " + player2Score);
	}
	
	/*
	 * Inner class for each tile that a game piece can exist in.
	 */
	public class GameTile extends JPanel{
		//position in game board
		private int row,
			col;
		
		public static final String NONE = "NONE",
				BLACK = "BLACK",
				WHITE = "WHITE",
				HIGHLIGHT_BLACK = "HIGHLIGHT_BLACK",
				HIGHLIGHT_WHITE = "HIGHLIGHT_WHITE";
		//game piece that currently exists in the cell
		private String gamePiece = NONE;
		
		public GameTile(int row, int col){
			this.row = row;
			this.col = col;
			addMouseListener(new ClickListener());
		}
		
		/*
		 * Sets the piece that will exist in this tile. The passed in 
		 * argument will be one of the final strings above.
		 * This should be set to NONE, BLACK, WHITE, etc.
		 */
		public void setPiece(String piece){
			gamePiece = piece;
		}
		
		/*
		 * returns the piece that exists in this tile.
		 */
		public String getPiece(){
			return gamePiece;
		}
		
		/*
		 * This method paints the tile. First, the background of the 
		 * tile is drawn, then the piece inside it is drawn using gamePiece.
		 */
		@Override
		protected void paintComponent(Graphics g){
			/*
			//TODO: highlight-black and highlight-white
			 * Delete this when you are done. Just clean up the game by giving each piece
			 * a border and maybe make the actual pieces look nice. 
			 * 
			 * 
			 * 
			 * 
			 * 
			 */
			
			
			//background paint
			g.setColor(Color.GREEN);
			g.fillRect(2, 2, OthelloConstants.TILE_SIZE - 4, OthelloConstants.TILE_SIZE - 4);
			
			
			//game piece paint
			if(gamePiece == BLACK){
				g.setColor(new Color(0, 0, 0, 255));
				g.fillOval(10, 8, 80, 80);
			}else if(gamePiece == WHITE){
				g.setColor(new Color(255, 255, 255, 255));
				g.fillOval(10, 10, 80, 80);
			}else if(gamePiece == HIGHLIGHT_WHITE){
				
				g.setColor(Color.WHITE);
				g.drawOval(40, 40, 5, 5); //replace with real call
			}else if(gamePiece == HIGHLIGHT_BLACK){
				g.setColor(Color.BLACK);
				g.drawOval(40, 40, 5, 5); //replace with real call
			}
		}
		
		/*
		 * When the tile is clicked, this listener will check if it is
		 * this player's turn and if this is a valid tile to select.
		 * If it is, this will call a method in the client that will 
		 * pass the selected tile info to the server. 
		 */
		private class ClickListener extends MouseAdapter{
			public void mouseClicked(MouseEvent event){
				//if this is the right player's turn, update the board and send the move
				if(thisPlayerTurn && (gamePiece == HIGHLIGHT_WHITE || gamePiece == HIGHLIGHT_BLACK)){
					gamePiece = thisPlayerColor;
					thisPlayerTurn = false;
					sendRow = row;
					sendCol = col;
					playerWaiting = false;
					repaint();
					printOutput("You made your move. Waiting for other player to make a move...");
					System.out.println(row + " " + col);
				}
			}
		}
	}
}

