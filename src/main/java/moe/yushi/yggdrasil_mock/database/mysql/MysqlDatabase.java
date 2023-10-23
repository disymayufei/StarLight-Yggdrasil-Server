package moe.yushi.yggdrasil_mock.database.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.NonNull;
import moe.yushi.yggdrasil_mock.database.YggdrasilDatabase;
import moe.yushi.yggdrasil_mock.exception.UserAlreadyExistedException;
import moe.yushi.yggdrasil_mock.texture.ModelType;
import moe.yushi.yggdrasil_mock.texture.Texture;
import moe.yushi.yggdrasil_mock.texture.TextureType;
import moe.yushi.yggdrasil_mock.utils.secure.EncryptUtils;
import moe.yushi.yggdrasil_mock.yggdrasil.YggdrasilCharacter;
import moe.yushi.yggdrasil_mock.yggdrasil.YggdrasilUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.*;

import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.m_profile_not_found;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.newForbiddenOperationException;

@SuppressWarnings("SqlNoDataSourceInspection")
@Component
public class MysqlDatabase implements YggdrasilDatabase {

    @Value("${database.mysql.host}")
    private String host;
    @Value("${database.mysql.port}")
    private String port;
    @Value("${database.mysql.driver-class-name}")
    private String driverClassName;
    @Value("${database.mysql.username}")
    private String username;
    @Value("${database.mysql.password}")
    private String password;
    @Value("${database.mysql.database-name}")
    private String databaseName;

    private @Autowired TextureStorage texturesStorage;

    private final HikariConfig config = new HikariConfig();

    private JdbcTemplate jdbcTemplate = null;

    protected JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }

    public static MysqlDatabase INSTANCE = null;

    @PostConstruct
    public void init() {
        if (host == null || host.isBlank()) {
            host = "localhost";
        }

        config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s?createDatabaseIfNotExist=true&useSSL=false&useUnicode=true&characterEncoding=utf-8", host, port, databaseName));
        config.setDriverClassName(driverClassName);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("autoCommit", "true");
        config.addDataSourceProperty("connectionTimeout", "5");
        config.addDataSourceProperty("idleTimeout", "60000");
        config.addDataSourceProperty("maxLifeTime", "6000");

        connect();

        INSTANCE = this;
    }

    public void connect() {
        if (jdbcTemplate == null) {
            try {
                jdbcTemplate = new JdbcTemplate(new HikariDataSource(config));
            }
            catch (Exception e) {
                throw new RuntimeException("MySQL database connect failed!", e);
            }
        }

        createUserTable();
        createCharacterTable();
        createTokenTable();
    }

    private void createUserTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `YggdrasilUser` (" +
                "`uid` BIGINT UNSIGNED AUTO_INCREMENT," +
                "`email` VARCHAR(50) NOT NULL," +
                "`uuid` VARCHAR(36) NOT NULL," +
                "`password` TEXT NOT NULL," +
                "`qq` BIGINT UNSIGNED," +
                "`permission` INT DEFAULT 0," +
                "INDEX `idx` (`qq`, `email`)," +
                "PRIMARY KEY (`uid`)" +
                ")";
        jdbcTemplate.update(sql);
    }

    private void createCharacterTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `YggdrasilCharacter` (" +
                "`uuid` VARCHAR(36) NOT NULL," +
                "`name` VARCHAR(16) NOT NULL," +
                "`skin` TEXT," +
                "`cape` TEXT," +
                "`elytra` TEXT," +
                "`model` VARCHAR(10) NOT NULL," +
                "`owner_uid` BIGINT UNSIGNED," +
                "`slot` INTEGER," +
                "PRIMARY KEY (`uuid`)" +
                ")";
        jdbcTemplate.update(sql);
    }

    private void createTokenTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `Token` (" +
                "`client_token` TEXT," +
                "`access_token` TEXT NOT NULL," +
                "`created_at` BIGINT UNSIGNED," +
                "`character` VARCHAR(36)," +
                "`user_uid` BIGINT UNSIGNED," +
                "PRIMARY KEY (`access_token`(100))" +
                ")";
        jdbcTemplate.update(sql);
    }

    public boolean tableNameExist(String tableName) {
        String sql = "SELECT COUNT(*) as count FROM information_schema.TABLES WHERE TABLE_NAME=?";
        Integer tableNum = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return tableNum != null && tableNum > 0;
    }

    public int getCharacterNum(long uid) {
        String sql = "SELECT COUNT(*) as count FROM YggdrasilCharacter WHERE `owner_uid`=?";
        Integer num = jdbcTemplate.queryForObject(sql, Integer.class, uid);
        return num == null ? 0 : num;
    }

    public boolean addUser(String email, String uuid, String password) {
        if (findUserByEmail(email).isEmpty()) {
            String sql = "INSERT INTO YggdrasilUser(email, uuid, password) VALUES(?, ?, ?)";

            int status = jdbcTemplate.update(sql, email, uuid, password);
            return status > 0;
        }
        else {
            throw new UserAlreadyExistedException("This email has already exists");
        }
    }

    public boolean bindQQ(String email, long qq) {
        String sql = "UPDATE YggdrasilUser SET qq=? WHERE `email`=?";
        Integer tableNum = jdbcTemplate.queryForObject(sql, Integer.class, qq, email);
        return tableNum != null && tableNum > 0;
    }

    public boolean addNewCharacter(String uuid, String name, ModelType modelType, long ownerUid) throws TooManyListenersException {
        if (findCharacterByName(name).isPresent()) {
            throw new UserAlreadyExistedException("This name has already exists");
        }

        if (getCharacterNum(ownerUid) > 2) {
            throw new TooManyListenersException("Your character has exceed the number limit!");
        }

        int slot = findFirstEmptySlot(ownerUid);

        String sql = "INSERT INTO YggdrasilCharacter(uuid, name, model, owner_uid, slot) VALUES(?, ?, ?, ?, ?)";
        int status = jdbcTemplate.update(sql, uuid, name, modelType.getModelName(), ownerUid, slot);
        return status > 0;
    }

    /**
     * 根据UID找到第一个可以插入玩家的空槽位
     * @param uid 待查找的UID值
     * @return 第一个空槽位
     */
    public int findFirstEmptySlot(long uid) {
        String sql = "SELECT * FROM YggdrasilCharacter WHERE (`owner_uid`=? AND `slot`=?)";
        int slot = 1;

        while (true) {
            try {
                jdbcTemplate.queryForObject(sql, Object.class, uid, slot);
                slot++;
            }
            catch (EmptyResultDataAccessException e) {
                return slot;
            }
        }
    }

    @Override
    public YggdrasilUser createNewUser(String email, String password, long uid) {
        return new YggdrasilUser(UUID.randomUUID(), email, password, uid);
    }

    @Override
    public YggdrasilCharacter createNewCharacter(UUID uuid, String name, YggdrasilUser owner) {
        return new YggdrasilCharacter(uuid, name, owner, TextureType.SKIN, TextureType.CAPE, TextureType.ELYTRA);
    }

    @Override
    public void setTexture(@NonNull UUID uuid, @Nullable Texture texture, @NonNull TextureType type) {
        if (findCharacterByUUID(uuid).isEmpty()) {
            throw newForbiddenOperationException(m_profile_not_found);
        }

        String sql = "UPDATE YggdrasilCharacter SET ?=? WHERE `uuid`=?";
        if (texture == null) {
            jdbcTemplate.update(sql, type.toString(), null, uuid);
        }
        else {
            jdbcTemplate.update(sql, type.toString(), texture.getHash(), uuid);
        }
    }

    @Override
    public String getTexture(@NonNull UUID uuid, @NonNull TextureType type) {
        if (findCharacterByUUID(uuid).isEmpty()) {
            throw newForbiddenOperationException(m_profile_not_found);
        }

        String sql = "SELECT ? FROM YggdrasilCharacter WHERE `uuid`=? ";

        return jdbcTemplate.queryForObject(sql, String.class, type.toString(), uuid);
    }

    public boolean verifyUserPassword(@NonNull String email, @NonNull String password) {
        Optional<YggdrasilUser> yggdrasilUserOptional = findUserByEmail(email);
        if (yggdrasilUserOptional.isEmpty()) {
            return false;
        }

        String hash = yggdrasilUserOptional.get().getPassword();
        return EncryptUtils.verifyArgon2Hash(hash, password);
    }

    public int getUserPermission(@NonNull String email) {
        if (findUserByEmail(email).isEmpty()) {
            throw newForbiddenOperationException(m_profile_not_found);
        }

        String sql = "SELECT `permission` FROM YggdrasilUser WHERE `email`=?";
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, email);

        return result == null ? 0 : result;
    }

    public Optional<YggdrasilUser> findUserByUID(long uid) {
        String sql = "SELECT * FROM YggdrasilUser WHERE `uid`=?";

        try {
            var resultList = jdbcTemplate.queryForList(sql, uid);
            if (!resultList.isEmpty()) {
                var result = resultList.get(0);
                YggdrasilUser user = new YggdrasilUser(
                                UUID.fromString((String)result.get("uuid")),
                                (String) result.get("email"),
                                (String) result.get("password"),
                                ((BigInteger) result.get("uid")).longValue()
                        );
                return Optional.of(user);
            }

        }
        catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public Optional<YggdrasilUser> findUserByEmail(String email) {
        String sql = "SELECT * FROM YggdrasilUser WHERE `email`=?";
        try {
            var resultList = jdbcTemplate.queryForList(sql, email);
            if (!resultList.isEmpty()) {
                var result = resultList.get(0);
                var userResult = new YggdrasilUser(
                                UUID.fromString((String)result.get("uuid")),
                                email,
                                (String) result.get("password"),
                                ((BigInteger) result.get("uid")).longValue()
                );

                userResult.addCharacters(findCharactersByUID(userResult.getUID()));

                return Optional.of(userResult);
            }

        }
        catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public Optional<YggdrasilCharacter> findCharacterByUUID(UUID uuid) {
        String sql = "SELECT * FROM YggdrasilCharacter WHERE `uuid`=?";
        return getYggdrasilCharacter(sql, uuid);
    }

    @Override
    public Optional<YggdrasilCharacter> findCharacterByName(String name) {
        String sql = "SELECT * FROM YggdrasilCharacter WHERE `name`=?";

        return getYggdrasilCharacter(sql, name);
    }

    @Override
    public List<YggdrasilCharacter> findCharactersByEmail(String email) {
        List<YggdrasilCharacter> characterList = new ArrayList<>();

        Optional<YggdrasilUser> user = findUserByEmail(email);
        if (user.isEmpty()) {
            return characterList;
        }

        String sql = "SELECT * FROM YggdrasilCharacter WHERE `owner_uid`=?";
        try {
            var resultList = jdbcTemplate.queryForList(sql, user.get().getUID());
            for (var result : resultList) {
                characterList.add(new YggdrasilCharacter(UUID.fromString((String)result.get("uuid")), (String)result.get("name"), user.get()));
            }
        }
        catch (EmptyResultDataAccessException e) {
            return characterList;
        }

        return characterList;
    }

    public List<YggdrasilCharacter> findCharactersByUID(long uid) {
        List<YggdrasilCharacter> characterList = new ArrayList<>();

        Optional<YggdrasilUser> user = findUserByUID(uid);
        if (user.isEmpty()) {
            return characterList;
        }

        String sql = "SELECT * FROM YggdrasilCharacter WHERE `owner_uid`=?";
        try {
            var resultList = jdbcTemplate.queryForList(sql, uid);
            for (var result : resultList) {
                characterList.add(new YggdrasilCharacter(UUID.fromString((String)result.get("uuid")), (String)result.get("name"), user.get()));
            }
        }
        catch (EmptyResultDataAccessException e) {
            return characterList;
        }

        return characterList;
    }

    @Override
    public List<YggdrasilUser> getUsers() {
        List<YggdrasilUser> userList = new ArrayList<>();

        String sql = "SELECT * FROM YggdrasilUser";
        try {
            var resultList = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> result : resultList) {
                userList.add(new YggdrasilUser(
                        UUID.fromString((String)result.get("uuid")),
                        (String) result.get("email"),
                        (String) result.get("password"),
                        ((BigInteger) result.get("uid")).longValue()
                ));
            }

        }
        catch (EmptyResultDataAccessException ignored) {}

        return userList;
    }

    @NonNull
    private Optional<YggdrasilCharacter> getYggdrasilCharacter(String sql, Object... values) {
        try {
            var maps = jdbcTemplate.queryForList(sql, values);
            if (!maps.isEmpty()) {
                var result = maps.get(0);
                Optional<YggdrasilUser> owner = findUserByUID(((BigInteger)result.get("uid")).longValue());
                if (owner.isEmpty()) {
                    return Optional.empty();
                }

                YggdrasilCharacter character = new YggdrasilCharacter(UUID.fromString((String)result.get("uuid")), (String)result.get("name"), owner.get());
                return Optional.of(character);
            }

        }
        catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    public TextureStorage getTexturesStorage() {
        return texturesStorage;
    }
}
