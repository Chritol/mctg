package bif3.tolan.swe1.mcg.database.respositories;

import bif3.tolan.swe1.mcg.exceptions.IdExistsException;
import bif3.tolan.swe1.mcg.exceptions.InvalidInputException;
import bif3.tolan.swe1.mcg.model.User;

import java.sql.SQLException;

public interface UserRepository {
    User getById(int id) throws SQLException;

    User getByUsername(String username) throws SQLException;

    void add(User user) throws SQLException, InvalidInputException, IdExistsException;
}