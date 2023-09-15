package moe.yushi.yggdrasil_mock.database.mysql;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.NonNull;
import moe.yushi.yggdrasil_mock.database.YggdrasilDatabase;
import moe.yushi.yggdrasil_mock.exception.FileUploadFailedException;
import moe.yushi.yggdrasil_mock.exception.UserAlreadyExistedException;
import moe.yushi.yggdrasil_mock.texture.ModelType;
import moe.yushi.yggdrasil_mock.texture.Texture;
import moe.yushi.yggdrasil_mock.texture.TextureType;
import moe.yushi.yggdrasil_mock.user.YggdrasilCharacter;
import moe.yushi.yggdrasil_mock.user.YggdrasilUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
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

    @Autowired
    private Texture.Storage textureStorage;

    private final HikariConfig config = new HikariConfig();

    // 以Email为key，User为value
    private final ConcurrentLinkedHashMap<String, YggdrasilUser> userCache =
            new ConcurrentLinkedHashMap.Builder<String, YggdrasilUser>()
                    .maximumWeightedCapacity(64)
                    .build();

    // 以UUID为key，Character为value
    private final ConcurrentLinkedHashMap<UUID, YggdrasilCharacter> characterCache =
            new ConcurrentLinkedHashMap.Builder<UUID, YggdrasilCharacter>()
            .maximumWeightedCapacity(64)
            .build();

    private JdbcTemplate jdbcTemplate = null;

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
        config.addDataSourceProperty("idleTimeout", "60");

        connect();
    }

    public void connect() {
        if (jdbcTemplate == null) {
            try {
                this.jdbcTemplate = new JdbcTemplate(new HikariDataSource(config));
            }
            catch (Exception e) {
                throw new RuntimeException("MySQL database connect failed!", e);
            }
        }

        createUserTable();
        createCharacterTable();
    }

    private void createUserTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `User` (" +
                "`uid` BIGINT UNSIGNED AUTO_INCREMENT," +
                "`email` VARCHAR(50) NOT NULL," +
                "`uuid` VARCHAR(36) NOT NULL," +
                "`password` VARCHAR(100) NOT NULL," +
                "`qq` BIGINT UNSIGNED," +
                "INDEX `idx` (`qq`, `email`)," +
                "PRIMARY KEY (`uid`)" +
                ")";
        jdbcTemplate.update(sql);
    }

    private void createCharacterTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `Character` (" +
                "`uuid` VARCHAR(36) NOT NULL," +
                "`name` VARCHAR(16) NOT NULL," +
                "`skin` TEXT," +
                "`cape` TEXT," +
                "`elytra` TEXT," +
                "`model` VARCHAR(10) NOT NULL," +
                "`owner_uid BIGINT UNSIGNED`," +
                "`slot` INT," +
                "PRIMARY KEY (`uuid`)" +
                ")";
        jdbcTemplate.update(sql);
    }

    public boolean tableNameExist(String tableName) {
        String sql = "SELECT COUNT(*) as count FROM information_schema.TABLES WHERE TABLE_NAME=?";
        Integer tableNum = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return tableNum != null && tableNum > 0;
    }

    public int getCharacterNum(long uid) {
        String sql = "SELECT COUNT(*) as count WHERE uid=?";
        Integer num = jdbcTemplate.queryForObject(sql, Integer.class, uid);
        return num == null ? 0 : num;
    }

    public boolean addUser(String email, String uuid, String password) {
        if (findUserByEmail(email).isEmpty()) {
            String sql = "INSERT INTO User(email, uuid, password) VALUES(?, ?, ?)";

            int status = jdbcTemplate.update(sql, email, uuid, password);
            return status > 0;
        }
        else {
            throw new UserAlreadyExistedException("This email has already exists");
        }
    }

    public boolean bindQQ(String email, long qq) {
        String sql = "UPDATE User SET qq=? WHERE email=?";
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

        String sql = "INSERT INTO Character(uuid, name, model, owner_uid, slot) VALUES(?, ?, ?, ?, ?)";
        int status = jdbcTemplate.update(sql, uuid, name, modelType.getModelName(), ownerUid, slot);
        return status > 0;
    }

    /**
     * 根据UID找到第一个可以插入玩家的空槽位
     * @param uid 待查找的UID值
     * @return 第一个空槽位
     */
    public int findFirstEmptySlot(long uid) {
        String sql = "SELECT * FROM Character WHERE (uid=? AND slot=?)";
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
    public void uploadTexture(UUID uuid, Texture texture, TextureType type) {
        if (findCharacterByUUID(uuid).isEmpty()) {
            throw newForbiddenOperationException(m_profile_not_found);
        }

        File rootDir = new File("Texture");
        if (!rootDir.isDirectory()) {
            rootDir.mkdirs();
        }

        File textureFile = new File(rootDir, texture.getHash() + ".png");
        if (!textureFile.isFile()) {
            try {
                textureFile.createNewFile();
            }
            catch (Exception e) {
                System.err.println("Failed to create the texture file!");
                e.printStackTrace();
                throw new FileUploadFailedException("Failed to create the texture file!");
            }

            try (FileOutputStream outputStream = new FileOutputStream(textureFile)) {
                outputStream.write(texture.getData());
            }
            catch (Exception e) {
                System.err.println("Failed to save the texture file to disk.");
                e.printStackTrace();
                throw new FileUploadFailedException("The texture file can not be written!");
            }
        }

        String sql = "UPDATE Character SET ?=? WHERE uuid=?";
        jdbcTemplate.update(sql, type, textureFile.getPath(), uuid);

    }

    public Optional<YggdrasilUser> findUserByUID(long uid) {
        for (var entry : userCache.entrySet()) {
            if (entry.getValue().getUID() == uid) {
                return Optional.of(userCache.get(entry.getKey()));  // 将该User的缓存状态刷新为活跃
            }
        }

        String sql = "SELECT * FROM User WHERE uid=?";

        try {
            var resultList = jdbcTemplate.queryForList(sql, uid);
            if (!resultList.isEmpty()) {
                var result = resultList.get(0);
                YggdrasilUser user = new YggdrasilUser(UUID.fromString((String)result.get("uuid")), (String) result.get("email"), (String) result.get("password"), ((BigInteger) result.get("uid")).longValue());
                userCache.put((String) result.get("email"), user);
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
        YggdrasilUser user = userCache.get(email);
        if (user != null) {
            return Optional.of(user);
        }

        String sql = "SELECT * FROM User WHERE email=?";
        try {
            var resultList = jdbcTemplate.queryForList(sql, email);
            if (!resultList.isEmpty()) {
                var result = resultList.get(0);
                var character = new YggdrasilUser(UUID.fromString((String)result.get("uuid")), email, (String) result.get("password"), ((BigInteger) result.get("uid")).longValue());
                userCache.put(email, character);
                return Optional.of(character);
            }

        }
        catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public Optional<YggdrasilCharacter> findCharacterByUUID(UUID uuid) {
        YggdrasilCharacter character = characterCache.get(uuid);
        if (character != null) {
            return Optional.of(character);
        }

        String sql = "SELECT * FROM Character WHERE uuid=?";
        return getYggdrasilCharacter(sql, uuid);
    }

    @Override
    public Optional<YggdrasilCharacter> findCharacterByName(String name) {
        for (var entry : characterCache.entrySet()) {
            if (name.equals(entry.getValue().getName())) {
                return Optional.of(characterCache.get(entry.getKey()));  // 刷新Character在内存中的状态为活跃
            }
        }

        String sql = "SELECT * FROM Character WHERE name=?";

        return getYggdrasilCharacter(sql, name);
    }

    @Override
    public List<YggdrasilCharacter> findCharactersByEmail(String email) {
        List<YggdrasilCharacter> characterList = new ArrayList<>();

        Optional<YggdrasilUser> user = findUserByEmail(email);
        if (user.isEmpty()) {
            return characterList;
        }

        String sql = "SELECT * FROM Character WHERE uid=?";
        try {
            var resultList = jdbcTemplate.queryForList(sql, email);
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

        String sql = "SELECT * FROM User";
        try {
            var resultList = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> result : resultList) {
                userList.add(new YggdrasilUser(UUID.fromString((String)result.get("uuid")), (String) result.get("email"), (String) result.get("password"), ((BigInteger) result.get("uid")).longValue()));
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
                characterCache.put(character.getUuid(), character);
                return Optional.of(character);
            }

        }
        catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }

        return Optional.empty();
    }
}
