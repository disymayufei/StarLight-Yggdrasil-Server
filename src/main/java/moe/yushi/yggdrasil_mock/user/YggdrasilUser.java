package moe.yushi.yggdrasil_mock.user;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static moe.yushi.yggdrasil_mock.utils.PropertiesUtils.properties;
import static moe.yushi.yggdrasil_mock.utils.UUIDUtils.unsign;

// 玩家的根源，一个玩家必然只能对应一个User，但是可以对应多个Character
@AllArgsConstructor
@ToString
public class YggdrasilUser {
    @NonNull
    private UUID id;
    @NonNull
    private String email;
    @NonNull
    private String password;
    private long uid;
    private final List<YggdrasilCharacter> characters = new CopyOnWriteArrayList<>();

    public @NonNull String getEmail() {
        return email;
    }


    public @NonNull String getPassword() {
        return password;
    }

    public long getUID() {
        return uid;
    }

    public String getUUID() {
        return id.toString();
    }

    public void addCharacters(YggdrasilCharacter character) {
        this.characters.add(character);
    }

    public List<YggdrasilCharacter> getCharacters() {
        return characters;
    }

    public Map<String, Object> toResponse() {
        return
                // @formatter:off
                ofEntries(
                        entry("id", unsign(id)),
                        entry("properties", properties(
                        ))
                );
        // @formatter:on
    }
}
