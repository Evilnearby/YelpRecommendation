package db.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import db.DBConnection;
import entity.Item;
import entity.Item.ItemBuilder;
import external.YelpAPI;

// This is a singleton pattern.
public class MySQLConnection implements DBConnection{
	private static MySQLConnection instance;

	public static MySQLConnection getInstance() {
		if (instance == null) {
			instance = new MySQLConnection();
		}
		return instance;
	}

	// Import java.sql.Connection. Don't use com.mysql.jdbc.Connection.
	private Connection conn = null;

	private MySQLConnection() {
		try {
			// Forcing the class representing the MySQL driver to load and
			// initialize.
			// The newInstance() call is a work around for some broken Java
			// implementations.
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conn = DriverManager.getConnection(MySQLDBUtil.URL);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close() {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) { 
				System.out.println((e.getMessage()));
			}
		}
	}

	public void setFavoriteItems(String userId, List<String> itemIds) {
		String query = "INSERT INTO history (user_id, item_id) VALUES (?, ?)";
		try {
			PreparedStatement statement = conn.prepareStatement(query);
			for (String itemId : itemIds) {
				statement.setString(1, userId);
				statement.setString(2, itemId);
				statement.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}	

	}

	public void unsetFavoriteItems(String userId, List<String> itemIds) {
		if (conn == null) {
			return;
		}
		String query = "DELETE FROM history WHERE user_id = ? AND item_id = ?"; 
		try {
			PreparedStatement statement = conn.prepareStatement(query); 
			for (String itemId: itemIds) {
				statement.setString(1, userId); 
				statement.setString(2, itemId); 
				statement.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public Set<String> getFavoriteItemIds(String userId) {
		Set<String> favoriteItems = new HashSet<>();
		try {
			String sql = "SELECT item_id from history WHERE user_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				String itemId = rs.getString("item_id");
				favoriteItems.add(itemId);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return favoriteItems;
	}

	public Set<Item> getFavoriteItems(String userId) {
		if (conn == null) {
			return new HashSet<>();
		}
		Set<Item> favoriteItems = new HashSet<>();
		Set<String> itemIds = getFavoriteItemIds(userId);

		try {
			for (String itemId : itemIds) {
				String sql = "SELECT * FROM items WHERE item_id = ?";
				PreparedStatement statement = conn.prepareStatement(sql);
				statement.setString(1, itemId);

				ResultSet rs = statement.executeQuery();

				ItemBuilder builder = new ItemBuilder();
				while (rs.next()) {
					builder.setItemId(rs.getString("item_id"));
					builder.setName(rs.getString("name"));
					builder.setAddress(rs.getString("address"));
					builder.setImageUrl(rs.getString("image_url"));
					builder.setUrl(rs.getString("url"));
					builder.setCategories(getCategories(itemId));
					// builder.setDistance(rs.getDouble("distance"));
					builder.setRating(rs.getDouble("rating"));

					favoriteItems.add(builder.build());
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return favoriteItems;
	}
	

	public Set<String> getCategories(String itemId) {
		if (conn == null) {
			return null;
		}
		Set<String> categories = new HashSet<>();
		try {
			String sql = "SELECT category from categories WHERE item_id = ? "; 
			PreparedStatement statement = conn.prepareStatement(sql); 
			statement.setString(1, itemId);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				categories.add(rs.getString("category"));
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return categories;
	}

	public void saveItem(Item item) {
		try {
			// First, insert into items table
			String sql = "INSERT IGNORE INTO items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, item.getItemId());
			statement.setString(2, item.getName());
			statement.setString(3, item.getCity());
			statement.setString(4, item.getState());
			statement.setString(5, item.getCountry());
			statement.setString(6, item.getZipcode());
			statement.setDouble(7, item.getRating());
			statement.setString(8, item.getAddress());
			statement.setDouble(9, item.getLatitude());
			statement.setDouble(10, item.getLongitude());
			statement.setString(11, item.getDescription());
			statement.setString(12, item.getSnippet());
			statement.setString(13, item.getSnippetUrl());
			statement.setString(14, item.getImageUrl());
			statement.setString(15, item.getUrl());
			statement.execute();

			// Second, update categories table for each category.
			sql = "INSERT IGNORE INTO categories VALUES (?,?)";
			for (String category : item.getCategories()) {
				statement = conn.prepareStatement(sql);
				statement.setString(1, item.getItemId());
				statement.setString(2, category);
				statement.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}


	}

	public List<Item> searchItems(String userId, double lat, double lon, String term) {
		// Connect to external API
		YelpAPI api = new YelpAPI(); // moved here
		List<Item> items = api.search(lat, lon, term);
		for (Item item : items) {
			// Save the item into our own db.
			saveItem(item);
		}
		return items;

	}

	@Override
	public List<Item> searchItems(double lat, double lon, String term) {
		// TODO Auto-generated method stub
		return null;
	}
}
