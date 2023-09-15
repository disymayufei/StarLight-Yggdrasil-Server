package moe.yushi.yggdrasil_mock.database;

import moe.yushi.yggdrasil_mock.texture.Texture;
import moe.yushi.yggdrasil_mock.texture.TextureType;
import moe.yushi.yggdrasil_mock.user.YggdrasilCharacter;
import moe.yushi.yggdrasil_mock.user.YggdrasilUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface YggdrasilDatabase {

	YggdrasilUser createNewUser(String email, String password, long uid);

	YggdrasilCharacter createNewCharacter(UUID uuid, String name, YggdrasilUser owner);

	void uploadTexture(UUID uuid, Texture texture, TextureType type);

	Optional<YggdrasilUser> findUserByEmail(String email);

	Optional<YggdrasilCharacter> findCharacterByUUID(UUID uuid);

	Optional<YggdrasilCharacter> findCharacterByName(String name);

	List<YggdrasilCharacter> findCharactersByEmail(String email);

	List<YggdrasilUser> getUsers();
}
