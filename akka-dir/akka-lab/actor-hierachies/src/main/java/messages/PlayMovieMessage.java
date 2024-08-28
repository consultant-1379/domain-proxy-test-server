package messages;

public class PlayMovieMessage {
	
	private String movieTitle;
	private int userId;
	
	public PlayMovieMessage(String movieTitle, int userId) {
		this.movieTitle = movieTitle;
		this.userId = userId;
	}

	public String getMovieTitle() {
		return movieTitle;
	}

	public int getUserId() {
		return userId;
	}
}
