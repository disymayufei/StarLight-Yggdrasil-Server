package moe.yushi.yggdrasil_mock.texture;

import moe.yushi.yggdrasil_mock.user.YggdrasilCharacter;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Collections.singletonMap;
import static java.util.Optional.empty;
import static java.util.Optional.of;

public enum TextureType {
    SKIN(character -> of(singletonMap("model", character.getModel().getModelName()))),
    CAPE,
    ELYTRA;

    private final Function<YggdrasilCharacter, Optional<Map<?, ?>>> metadataFunc;

    TextureType() {
        this(dummy -> empty());
    }

    TextureType(Function<YggdrasilCharacter, Optional<Map<?, ?>>> metadataFunc) {
        this.metadataFunc = metadataFunc;
    }

    public Optional<Map<?, ?>> getMetadata(YggdrasilCharacter character) {
        return metadataFunc.apply(character);
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
