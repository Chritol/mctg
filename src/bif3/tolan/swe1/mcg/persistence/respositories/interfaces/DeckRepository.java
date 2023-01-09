package bif3.tolan.swe1.mcg.persistence.respositories.interfaces;

import java.sql.SQLException;

public interface DeckRepository {
    void createDeckForUser(int userId) throws SQLException;

    int getDeckIdByUserId(int userId) throws SQLException;
}