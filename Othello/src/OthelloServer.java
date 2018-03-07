import java.awt.BorderLayout;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.util.Date;

/*
 * Holds the server that will control the flow of the game for the clients.
 * Methods to include:
 * 		
 */
public class OthelloServer implements OthelloConstants{
	
	JTextArea log;
	private DataInputStream fromPlayer1,
		fromPlayer2;
	private DataOutputStream toPlayer1,
		toPlayer2;
	
	private GameBoard board;
	
	public static void main(String[] args){
		OthelloServer server = new OthelloServer();
	}
	
	//build the server window and establish connection using constructor
	public OthelloServer(){
		board = new GameBoard();
		//JFrame that creates the window, sets the close X option, title, size, and visisbility
		JFrame window = new JFrame();
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setTitle("Reversi Server: " + OthelloConstants.SERVER_SOCKET);
		window.setSize(OthelloConstants.WINDOW_WIDTH, OthelloConstants.WINDOW_HEIGHT);
		window.setVisible(true);
		
		//log window that holds communication details. Increase the font
		log = new JTextArea();
		log.setFont(log.getFont().deriveFont(18f));
		log.setEditable(false);
		
		//add the log to a scroll pane to allow text to have another page
		JScrollPane scrollPane = new JScrollPane(log);
		
		//add the communication log to the window
		window.add(scrollPane, BorderLayout.CENTER);
		window.revalidate();		
		
		// output time connected
		printOutput("Server Established");
		
		try{
			// Create server socket
			ServerSocket socket = new ServerSocket(8000);
			printOutput("Server started at socket 8000");
			
			int sessionNum = 1;
			// create a session
			while(true){
				printOutput("waiting for players for session "+sessionNum);
				// connect player 1 
				Socket player1 = socket.accept();
				printOutput("Player 1 joined");
				printOutput("Player 1's IP address is " + player1.getInetAddress().getHostAddress());
				// let the client know first player
				fromPlayer1 = new DataInputStream(player1.getInputStream());
				toPlayer1 = new DataOutputStream(player1.getOutputStream());
				toPlayer1.writeInt(PLAYER1);
				
				// connect player 2
				Socket player2 = socket.accept();
				printOutput("Player 2 joined");
				printOutput("Player 2's IP address is " + player2.getInetAddress().getHostAddress());

				// let the client know second player
				fromPlayer2 = new DataInputStream(player2.getInputStream());
				toPlayer2 = new DataOutputStream(player2.getOutputStream());
				toPlayer2.writeInt(PLAYER2);
				
				// display session
				printOutput("Session number"+sessionNum);
				// create thread
				GameSession thread = new GameSession(player1, player2);
				// start new thread
				new Thread(thread).start(); 
				
			}
			
		}catch(IOException e){
			System.out.println("quit game.virus");
		}
		
		
	}
	
	private class GameSession implements Runnable, OthelloConstants{
		private Socket player1;
		private Socket player2; 
		
		private boolean keepPlaying = true; 
		
		// constructor for thread
		public GameSession(Socket player1, Socket player2){
			this.player1 = player1;
			this.player2 = player2;
		}

		@Override
		public void run() {
			try{
				toPlayer1.writeInt(1);
				while(true){
					int row = 0;
					int col = 0;
					// player 1 move if they can make a move
					if(!noMoreMoves(board.BLACK)){
						row = fromPlayer1.readInt();
						col = fromPlayer1.readInt();
						board.setPiece(row, col, board.BLACK);
						board.updateBoard(row, col, board.BLACK);
						
						sendMove(toPlayer2, row, col);
					}else{
						toPlayer1.writeInt(NOMOVE);
					}
									
					// check victory condition 
					if(isFull() || noMoreMoves()){
						checkWinner();
						break;
					}
					
					// give player 2 the board is they have a move
					if(!noMoreMoves(board.WHITE)){
						toPlayer2.writeInt(CONTINUE);
						
						// receive player 2 move
						row = fromPlayer2.readInt();
						col = fromPlayer2.readInt();
						board.setPiece(row, col, board.WHITE);
						board.updateBoard(row, col, board.WHITE); 
						
						sendMove(toPlayer1, row, col);
					}else{
						toPlayer2.writeInt(NOMOVE);
					}
					
					
					// check victory condition 
					if(isFull() || noMoreMoves()){
						checkWinner();
						break;
					}
					
					// Tell player 1 to go if they have a move
					if(!noMoreMoves(board.BLACK)){
						toPlayer1.writeInt(CONTINUE);
					}
				}
				
			}catch(IOException e){
				System.out.println("trojan");
				
			}
		} 
		
		
	}
	
	private boolean isFull(){
		return board.isFull();
	}
	/*
	 * Returns true if no more moves can be made by any player
	 */
	private boolean noMoreMoves(){
		return(noMoreMoves(GameBoard.BLACK) && noMoreMoves(GameBoard.WHITE));
	}
	
	/*
	 * returns true if no more moves can be made by the passed in player
	 */
	private boolean noMoreMoves(String color){
		return board.noMoreMoves(color);
	}
	
	private void checkWinner() throws IOException{
		int winner = board.checkWinner();
		
		toPlayer1.writeInt(winner);
		toPlayer2.writeInt(winner);
	}
	
	private void sendMove(DataOutputStream toPlayer, int row, int col) throws IOException{
		toPlayer.writeInt(row);
		toPlayer.writeInt(col);
	}
	
	
	private void printOutput(String output){
		Date date = new Date();
		log.append(date.toString()+": "+output+"\n");
	}
	
	/*
	 * GameBoard class that holds the string representation of the board.
	 */
	private class GameBoard{
		private String[][] board = new String[8][8];
		public static final String NONE = "NONE",
				WHITE = "WHITE",
				BLACK = "BLACK";
	
		public GameBoard(){
			//set up the game board as empty
			for(int i = 0; i < 8; i++){
				for(int j = 0; j < 8; j++){
					board[i][j] = NONE;
				}
			}
			
			//set center pieces
			board[3][3] = (BLACK);
			board[3][4] = (BLACK);
			board[4][3] = (WHITE);
			board[4][4] = (WHITE);
		}
		
		public void setPiece(int row, int col, String piece){
			board[row][col] = piece;
		}
		public String getPiece(int row, int col){
			return board[row][col];
		}
		
		public boolean noMoreMoves(String color){
			String otherColor = BLACK;
			if(color == BLACK){
				otherColor = WHITE;
			}
			//for each tile
			for(int i = 0; i < board.length; i++){
				for(int j = 0; j < board[0].length; j++){
					if(board[i][j] == NONE){
						
						//check tiles above
						if(i > 0){
							//if the next piece is the other player, make sure there is a this player color in the line
							if(board[i - 1][j] == otherColor){
								//check every piece until none or opposite color hit
								int tileRow = i - 1;
								while(tileRow > 0){
									if(board[tileRow][j] == NONE){
										break;
									}else if(board[tileRow][j] == color){
										return false;
									}
									tileRow--;
								}
							}
						}
						
						//check tiles below
						if(i < board.length - 1){
							//if the next piece is the other player, make sure there is a this player color in the line
							if(board[i + 1][j] == otherColor){
								//check every piece until none or opposite color hit
								int tileRow = i + 1;
								while(tileRow < board.length){
									if(board[tileRow][j] == NONE){
										break;
									}else if(board[tileRow][j] == color){
										return false;
									}
									tileRow++;
								}
							}
						}
					
						//check tiles left
						if(j > 0){
							//if the next piece is the other player, make sure there is a this player color in the line
							if(board[i][j - 1] == otherColor){
								//check every piece until none or opposite color hit
								int tileCol = j - 1;
								while(tileCol > 0){
									if(board[i][tileCol] == NONE){
										break;
									}else if(board[i][tileCol] == color){
										return false;
									}
									tileCol--;
								}
							}
						}
					
						//check tiles right
						if(j < board[0].length - 1){
							//if the next piece is the other player, make sure there is a this player color in the line
							if(board[i][j + 1] == otherColor){
								//check every piece until none or opposite color hit
								int tileCol = j + 1;
								while(tileCol < board[0].length){
									if(board[i][tileCol] == NONE){
										break;
									}else if(board[i][tileCol] == color){
										return false;
									}
									tileCol++;
								}
							}
						}
					
						//check tiles top-left diagonal
						if(i > 0 && j > 0){
							//if the next piece is the other player, make sure there is a this player color in the line
							if(board[i - 1][j - 1] == otherColor){
								//check every piece until none or opposite color hit
								int tileCol = j - 1;
								int tileRow = i - 1;
								while(tileCol > 0 && tileRow > 0){
									if(board[tileRow][tileCol] == NONE){
										break;
									}else if(board[tileRow][tileCol] == color){
										return false;
									}
									tileCol--;
									tileRow--;
								}
							}
						}
					
						//check tiles top_right diagonal
						if(i > 0 && j < board[0].length - 1){
							//if the next piece is the other player, make sure there is a this player color in the line
							if(board[i - 1][j + 1] == otherColor){
								//check every piece until none or opposite color hit
								int tileCol = j + 1;
								int tileRow = i - 1;
								while(tileRow > 0 && tileCol < board[0].length){
									if(board[tileRow][tileCol] == NONE){
										break;
									}else if(board[tileRow][tileCol] == color){
										return false;
									}
									tileCol++;
									tileRow--;
								}
							}
						}
					
						//check tiles bottom_right diagonal
						if(i < board.length - 1 && j < board[0].length - 1){
							//if the next piece is the other player, make sure there is a this player color in the line
							if(board[i + 1][j + 1] == otherColor){
								//check every piece until none or opposite color hit
								int tileCol = j + 1;
								int tileRow = i + 1;
								while(tileRow < board.length && tileCol < board[0].length){
									if(board[tileRow][tileCol] == NONE){
										break;
									}else if(board[tileRow][tileCol] == color){
										return false;
									}
									tileCol++;
									tileRow++;
								}
							}
						}
					
						//check tiles bottom_left diagonal
						if(i < board.length - 1 && j > 0){
							//if the next piece is the other player, make sure there is a this player color in the line
							if(board[i + 1][j - 1] == otherColor){
								//check every piece until none or opposite color hit
								int tileCol = j - 1;
								int tileRow = i + 1;
								while(tileRow < board.length && tileCol > 0){
									if(board[tileRow][tileCol] == NONE){
										break;
									}else if(board[tileRow][tileCol] == color){
										return false;
									}
									tileCol--;
									tileRow++;
								}
							}
						}
					}
				}
			}
			
			return true;
		}
		
		public boolean isFull(){
			int count = 0;
			for(int i = 0; i < board.length; i++){
				for(int j = 0; j < board[0].length; j++){
					if(board[i][j] == BLACK || board[i][j] == WHITE){
						count++;
					}
				}
			}
			
			if(count == board.length * board[0].length){
				return true;
			}
			
			return false;
		}
		
		/*
		 * returns the winner of the game
		 */
		public int checkWinner(){
			int player1Num = 0,
					player2Num = 0;
			
			for(int i = 0; i < board.length; i++){
				for(int j = 0; j < board[0].length; j++){
					if(board[i][j] == BLACK){
						player1Num++;
					}else if(board[i][j] == WHITE){
						player2Num++;
					}
				}
			}
			
			//check victor
			if(player1Num > player2Num){
				return PLAYER1;
			}else if(player2Num > player1Num){
				return PLAYER2;
			}else{		//draw
				return DRAW;
			}
		}
		
		/*
		 * After a piece is added to the board, this will use game
		 * logic to check if any pieces on the board need to change
		 * color.
		 */
		public void updateBoard(int newRow, int newCol, String color){
			String thisColor = color;
			
			boolean hasSameColorPiece = false,
					hasGaps = false;
			int sameColorRow = 0,
				sameColorCol = 0;
			
			//check pieces above this one
			//first find the same color piece (if exists) and check for blank spots
			if(newRow > 0){
				for(int i = newRow - 1; i >= 0; i--){
					if(board[i][newCol] == thisColor){
						hasSameColorPiece = true;
						sameColorRow = i;
						sameColorCol = newCol;
						break;
					}
				}
				
				if(hasSameColorPiece){
					for(int i = newRow; i > sameColorRow; i--){
						if(board[i][newCol] == NONE){
							hasGaps = true;
							break;
						}
					}
				}
				
				//if no blank spots, convert every piece between the two
				if(!hasGaps && hasSameColorPiece){
					for(int i = newRow; i > sameColorRow; i--){
						board[i][newCol] = thisColor;
					}
				}
			}
			
			//below
			hasSameColorPiece = false;
			hasGaps = false;
			
			if(newRow < board.length - 1){
				for(int i = newRow + 1; i <= board.length - 1; i++){
					if(board[i][newCol] == thisColor){
						hasSameColorPiece = true;
						sameColorRow = i;
						sameColorCol = newCol;
						break;
					}
				}
				
				if(hasSameColorPiece){
					for(int i = newRow; i < sameColorRow; i++){
						if(board[i][newCol] == NONE){
							hasGaps = true;
							break;
						}
					}
				}
				
				//if no blank spots, convert every piece between the two
				if(!hasGaps && hasSameColorPiece){
					for(int i = newRow; i < sameColorRow; i++){
						board[i][newCol] = thisColor;
					}
				}
			}
			
			//right
			hasSameColorPiece = false;
			hasGaps = false;
			
			if(newCol < board.length - 1){
				for(int i = newCol + 1; i <= board.length - 1; i++){
					if(board[newRow][i] == thisColor){
						hasSameColorPiece = true;
						sameColorRow = newRow;
						sameColorCol = i;
						break;
					}
				}
				
				if(hasSameColorPiece){
					for(int i = newCol; i < sameColorCol; i++){
						if(board[newRow][i] == NONE){
							hasGaps = true;
							break;
						}
					}
				}
				
				//if no blank spots, convert every piece between the two
				if(!hasGaps && hasSameColorPiece){
					for(int i = newCol; i < sameColorCol; i++){
						board[newRow][i] = thisColor;
					}
				}
			}
			
			//left
			hasSameColorPiece = false;
			hasGaps = false;
			
			if(newCol > 0){
				for(int i = newCol - 1; i >= 0; i--){
					if(board[newRow][i] == thisColor){
						hasSameColorPiece = true;
						sameColorRow = newRow;
						sameColorCol = i;
						break;
					}
				}
				
				if(hasSameColorPiece){
					for(int i = newCol; i > sameColorCol; i--){
						if(board[newRow][i] == NONE){
							hasGaps = true;
							break;
						}
					}
				}
				
				//if no blank spots, convert every piece between the two
				if(!hasGaps && hasSameColorPiece){
					for(int i = newCol; i > sameColorCol; i--){
						board[newRow][i] = thisColor;
					}
				}
			}
			
			//top-left diag
			hasSameColorPiece = false;
			hasGaps = false;
			
			if(newCol > 0 && newRow > 0){
				for(int i = newRow - 1, j = newCol - 1; i >= 0 && j >= 0; i--, j-- ){
					if(board[i][j] == thisColor){
						hasSameColorPiece = true;
						sameColorRow = i;
						sameColorCol = j;
						break;
					}
				}
				
				if(hasSameColorPiece){
					for(int i = newRow, j = newCol; i > sameColorRow && j > sameColorCol; i--, j--){
						if(board[i][j] == NONE){
							hasGaps = true;
							break;
						}
					}
				}
				
				if(!hasGaps && hasSameColorPiece){
					for(int i = newRow, j = newCol; i > sameColorRow && j > sameColorCol; i--, j--){
						board[i][j] = thisColor;
					}
				}
			}
			
			//top-right diag
			hasSameColorPiece = false;
			hasGaps = false;
			
			if(newCol < board.length - 1 && newRow > 0){
				for(int i = newRow - 1, j = newCol + 1; i >= 0 && j <= board.length - 1; i--, j++){
					if(board[i][j] == thisColor){
						hasSameColorPiece = true;
						sameColorRow = i;
						sameColorCol = j;
						break;
					}
				}
				
				if(hasSameColorPiece){
					for(int i = newRow, j = newCol; i > sameColorRow && j < sameColorCol; i--, j++){
						if(board[i][j] == NONE){
							hasGaps = true;
							break;
						}
					}
				}
				
				if(!hasGaps && hasSameColorPiece){
					for(int i = newRow, j = newCol; i > sameColorRow && j < sameColorCol; i--, j++){
						board[i][j] = thisColor;
					}
				}
			}
			
			//bottom-right diag
			hasSameColorPiece = false;
			hasGaps = false;
			
			if(newCol < board.length - 1 && newRow < board.length - 1){
				for(int i = newRow + 1, j = newCol + 1; i <= board.length - 1 && j <= board.length - 1; i++, j++){
					if(board[i][j] == thisColor){
						hasSameColorPiece = true;
						sameColorRow = i;
						sameColorCol = j;
						break;
					}
				}
				
				if(hasSameColorPiece){
					for(int i = newRow, j = newCol; i < sameColorRow && j < sameColorCol; i++, j++){
						if(board[i][j] == NONE){
							hasGaps = true;
							break;
						}
					}
				}
				
				if(!hasGaps && hasSameColorPiece){
					for(int i = newRow, j = newCol; i < sameColorRow && j < sameColorCol; i++, j++){
						board[i][j] = thisColor;
					}
				}
			}
			
			//bottom-left diag
			hasSameColorPiece = false;
			hasGaps = false;
			
			if(newCol > 0 && newRow < board.length - 1){
				for(int i = newRow + 1, j = newCol - 1; i <= board.length - 1 && j >= 0; i++, j--){
					if(board[i][j] == thisColor){
						hasSameColorPiece = true;
						sameColorRow = i;
						sameColorCol = j;
						break;
					}
				}
				
				if(hasSameColorPiece){
					for(int i = newRow, j = newCol; i < sameColorRow && j > sameColorCol; i++, j--){
						if(board[i][j] == NONE){
							hasGaps = true;
							break;
						}
					}
				}
				
				if(!hasGaps && hasSameColorPiece){
					for(int i = newRow, j = newCol; i < sameColorRow && j > sameColorCol; i++, j--){
						board[i][j] = thisColor;
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
					if(board[i][j] == BLACK){
						player1Score++;
					}else if(board[i][j] == WHITE){
						player2Score++;
					}
				}
			}
			
			printOutput("Player 1 score: " + player1Score + "       Player 2 score: " + player2Score);
		}
	}
}
