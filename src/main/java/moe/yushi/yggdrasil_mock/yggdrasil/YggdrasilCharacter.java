package moe.yushi.yggdrasil_mock.yggdrasil;

import lombok.NonNull;
import moe.yushi.yggdrasil_mock.database.mysql.MysqlDatabase;
import moe.yushi.yggdrasil_mock.texture.ModelType;
import moe.yushi.yggdrasil_mock.texture.Texture;
import moe.yushi.yggdrasil_mock.texture.TextureType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.singletonMap;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.stream.Collectors.joining;
import static moe.yushi.yggdrasil_mock.utils.PropertiesUtils.base64Encoded;
import static moe.yushi.yggdrasil_mock.utils.PropertiesUtils.properties;
import static moe.yushi.yggdrasil_mock.utils.UUIDUtils.unsign;

public class YggdrasilCharacter {
    @NonNull
    private UUID uuid;
    @NonNull
    private String name;
    private ModelType model = ModelType.STEVE;
    private final Set<TextureType> uploadableTextures = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private YggdrasilUser owner;

    public YggdrasilCharacter(@NonNull UUID uuid, @NonNull String name, YggdrasilUser owner, TextureType... uploadableTextures) {
        this.uuid = uuid;
        this.name = name;
        this.owner = owner;

        this.uploadableTextures.addAll(Arrays.asList(uploadableTextures));
    }

    public @NonNull UUID getUuid() {
        return uuid;
    }

    public void setUuid(@NonNull UUID uuid) {
        this.uuid = uuid;
    }

    public @NonNull String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public ModelType getModel() {
        return model;
    }

    public void setModel(ModelType model) {
        this.model = model;
    }

    public YggdrasilUser getOwner() {
        return owner;
    }

    public void setOwner(YggdrasilUser owner) {
        this.owner = owner;
    }

    public Set<TextureType> getUploadableTextures() {
        return uploadableTextures;
    }

    public Map<String, Object> toSimpleResponse() {
        return
                // @formatter:off
                ofEntries(
                        entry("id", unsign(uuid)),
                        entry("name", name)
                );
        // @formatter:on
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toCompleteResponse(boolean signed) {
        var texturesResponse = new LinkedHashMap<>();
        var textures = new HashMap<TextureType, Texture>();

        for (TextureType type : uploadableTextures) {
            MysqlDatabase mysqlDatabase = MysqlDatabase.INSTANCE;
            String hash = mysqlDatabase.getTexture(uuid, type);
            mysqlDatabase.getTexturesStorage().getTexture(hash).ifPresent((texture) -> textures.put(type, texture));
        }

        textures.forEach((type, texture) -> {
            // @formatter:off
            texturesResponse.put(type, type.getMetadata(this)
                    .map(metadata -> ofEntries(
                            entry("url", texture.url),
                            entry("metadata", metadata)
                    ))
                    .orElseGet(() -> singletonMap("url", texture.url))
            );
            // @formatter:on
        });

        var properties = new ArrayList<Map.Entry<String, String>>();
        // @formatter:off
        properties.add(
                entry("textures", base64Encoded(
                        entry("timestamp", System.currentTimeMillis()),
                        entry("profileId", unsign(uuid)),
                        entry("profileName", name),
                        entry("textures", texturesResponse)
                ))
        );
        // @formatter:on

        if (!uploadableTextures.isEmpty()) {
            // @formatter:off
            properties.add(
                    entry("uploadableTextures",
                            uploadableTextures.stream()
                                    .map(type -> type.name().toLowerCase())
                                    .collect(joining(","))
                    )
            );
            // @formatter:on
        }

        return
                // @formatter:off
                ofEntries(
                        entry("id", unsign(uuid)),
                        entry("name", name),
                        entry("properties", properties(signed, properties.toArray(Map.Entry[]::new)))
                );
        // @formatter:on
    }
}
