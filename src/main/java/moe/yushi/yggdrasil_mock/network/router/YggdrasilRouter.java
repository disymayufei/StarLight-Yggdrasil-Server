package moe.yushi.yggdrasil_mock.network.router;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import moe.yushi.yggdrasil_mock.ServerMeta;
import moe.yushi.yggdrasil_mock.SessionAuthenticator;
import moe.yushi.yggdrasil_mock.database.mysql.MysqlDatabase;
import moe.yushi.yggdrasil_mock.database.mysql.TokenStore;
import moe.yushi.yggdrasil_mock.database.mysql.TokenStore.AvailableLevel;
import moe.yushi.yggdrasil_mock.database.mysql.TokenStore.Token;
import moe.yushi.yggdrasil_mock.network.RateLimiter;
import moe.yushi.yggdrasil_mock.texture.ModelType;
import moe.yushi.yggdrasil_mock.texture.Texture;
import moe.yushi.yggdrasil_mock.texture.TextureType;
import moe.yushi.yggdrasil_mock.utils.secure.EncryptUtils;
import moe.yushi.yggdrasil_mock.yggdrasil.YggdrasilCharacter;
import moe.yushi.yggdrasil_mock.yggdrasil.YggdrasilUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.GsonJsonParser;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.constraints.NotBlank;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.stream.Collectors.toList;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.*;
import static moe.yushi.yggdrasil_mock.utils.UUIDUtils.*;
import static org.springframework.http.CacheControl.maxAge;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.MediaType.IMAGE_PNG;
import static org.springframework.http.ResponseEntity.*;

@Validated
@RestController
public class YggdrasilRouter {

	private final Logger logger = LoggerFactory.getLogger(YggdrasilRouter.class);

	private @Autowired ServerMeta meta;
	private @Autowired RateLimiter rateLimiter;
	private @Autowired TokenStore tokenStore;
	private @Autowired SessionAuthenticator sessionAuth;
	private @Autowired MysqlDatabase mysqlDatabase;
	private @Value("${yggdrasil.core.login-with-character-name}") boolean loginWithCharacterName;

	private final Gson GSON = new GsonBuilder().setLenient().create();

	@GetMapping("/")
	public ServerMeta root() {
		return meta;
	}

	@GetMapping("/status")
	public Map<?, ?> status() {
		return ofEntries(
				entry("user.count", mysqlDatabase.getUsers().size()),
				entry("token.count", tokenStore.tokensCount()),
				entry("pendingAuthentication.count", sessionAuth.pendingAuthenticationsCount()));
	}

	@PostMapping("/authserver/authenticate")
	public Map<?, ?> authenticate(@RequestBody @Valid LoginRequest req) {
		YggdrasilUser user;
		YggdrasilCharacter character = null;
		if (loginWithCharacterName) {
			character = mysqlDatabase.findCharacterByName(req.username).orElse(null);
		}
		if (character == null) {
			user = passwordAuthenticated(req.username, req.password);
		} else {
			user = passwordAuthenticated(character.getOwner().getEmail(), req.password);
		}

		if (req.clientToken == null)
			req.clientToken = randomUnsignedUUID();

		var token = tokenStore.acquireToken(user, req.clientToken, character);

		var response = new LinkedHashMap<>();
		response.put("accessToken", token.getAccessToken());
		response.put("clientToken", token.getClientToken());
		response.put("availableProfiles",
				user.getCharacters().stream()
						.map(YggdrasilCharacter::toSimpleResponse)
						.collect(toList()));
		token.getBoundCharacter().ifPresent(
				it -> response.put("selectedProfile", it.toSimpleResponse()));

		if (req.requestUser)
			response.put("user", user.toResponse());

		return response;
	}

	@PostMapping("/authserver/refresh")
	public Map<?, ?> refresh(@RequestBody @Valid RefreshRequest req) {
		var characterToSelect = req.selectedProfile == null ? null
				: mysqlDatabase.findCharacterByUUID(toUUID(req.selectedProfile.id))
						.orElseThrow(() -> newIllegalArgumentException(m_profile_not_found));

		if (characterToSelect != null && !characterToSelect.getName().equals(req.selectedProfile.name))
			throw newIllegalArgumentException(m_profile_not_found);

		var oldToken = authenticateAndConsume(req.accessToken, req.clientToken, AvailableLevel.PARTIAL,
				token -> {
					if (characterToSelect != null) {
						if (token.getBoundCharacter().isPresent())
							throw newIllegalArgumentException(m_token_already_assigned);

						if (characterToSelect.getOwner() != token.getUser())
							throw newForbiddenOperationException(m_access_denied);
					}
					return true;
				});

		var newToken = tokenStore.acquireToken(oldToken.getUser(), oldToken.getClientToken(),
				characterToSelect == null ? oldToken.getBoundCharacter().orElse(null) : characterToSelect);

		var response = new LinkedHashMap<>();
		response.put("accessToken", newToken.getAccessToken());
		response.put("clientToken", newToken.getClientToken());
		newToken.getBoundCharacter().ifPresent(
				it -> response.put("selectedProfile", it.toSimpleResponse()));

		if (req.requestUser)
			response.put("user", newToken.getUser().toResponse());

		return response;
	}

	@PostMapping("/authserver/validate")
	@ResponseStatus(NO_CONTENT)
	public void validate(@RequestBody @Valid ValidateRequest req) {
		authenticate(req.accessToken, req.clientToken, AvailableLevel.COMPLETE);
	}

	@PostMapping("/authserver/invalidate")
	@ResponseStatus(NO_CONTENT)
	public void invalidate(@RequestBody @Valid InvalidateRequest req) {
		tokenStore.authenticateAndConsume(req.accessToken, null, AvailableLevel.PARTIAL, dummy -> true);
	}

	@PostMapping("/authserver/signout")
	@ResponseStatus(NO_CONTENT)
	public void signout(@RequestBody @Valid SignoutRequest req) {
		var user = passwordAuthenticated(req.username, req.password);
		tokenStore.revokeAll(user);
	}

	@PostMapping("/sessionserver/session/minecraft/join")
	@ResponseStatus(NO_CONTENT)
	public void joinServer(@RequestBody @Valid JoinServerRequest req, ServerHttpRequest http) {
		var token = authenticate(req.accessToken, null, AvailableLevel.COMPLETE);
		if (token.getBoundCharacter().isPresent() &&
				unsign(token.getBoundCharacter().get().getUuid()).equals(req.selectedProfile)) {
			var ip = Optional.ofNullable(http.getRemoteAddress())
					.map(addr -> addr.getAddress().getHostAddress());
			sessionAuth.joinServer(token, req.serverId, ip);
		} else {
			throw newForbiddenOperationException("Invalid profile.");
		}
	}

	@GetMapping("/sessionserver/session/minecraft/hasJoined")
	public ResponseEntity<?> hasJoinedServer(ServerHttpRequest request, @RequestParam String serverId, @RequestParam String username, @RequestParam Optional<String> ip) {
		Optional<ResponseEntity<?>> localResponse = sessionAuth.verifyUser(username, serverId, ip)
				.map(character -> ok(character.toCompleteResponse(true)));

		if (localResponse.isPresent()) {
			return localResponse.get();
		}
		else {  // 如果找不到该玩家的缓存，则将该请求重定向只Mojang官方服务器，以兼容正版玩家

			// 获取url中携带的参数

			MultiValueMap<String, String> query = request.getQueryParams();

			StringBuilder target = new StringBuilder("https://sessionserver.mojang.com/session/minecraft/hasJoined");
			if (!query.isEmpty()) {
				target.append("?");

				for (Map.Entry<String, List<String>> param : query.entrySet()) {
					if (param.getValue().size() > 0) {
						target.append(param.getKey());
						target.append("=");
						target.append(param.getValue().get(0));
						target.append("&");
					}
				}
			}

			RestTemplate restTemplate = new RestTemplate();
			restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));

			// 获取到请求头
			HttpHeaders headers = request.getHeaders();

			// 构造HttpEntity，新请求会携带本次请求的请求头
			HttpEntity<String> entity = new HttpEntity<>(headers);

			ResponseEntity<String> response = restTemplate.getForEntity(target.toString(), String.class, entity);

			GsonJsonParser parser = new GsonJsonParser();
			Map<String, Object> body = parser.parseMap(response.getBody());
			System.out.println(body);

			return response;
		}
	}

	@PostMapping("/api/profiles/minecraft")
	public Stream<Map<?, ?>> queryProfiles(@RequestBody List<String> names) {
		return names.stream()
				.distinct()
				.map(mysqlDatabase::findCharacterByName)
				.flatMap(Optional::stream)
				.map(YggdrasilCharacter::toSimpleResponse);
	}

	@GetMapping("/sessionserver/session/minecraft/profile/{uuid:[a-f0-9]{32}}")
	public ResponseEntity<?> profile(@PathVariable String uuid, @RequestParam(required = false) String unsigned) {
		var signed = "false".equals(unsigned);
		return mysqlDatabase.findCharacterByUUID(toUUID(uuid))
				.map(character -> ok(character.toCompleteResponse(signed)))
				.orElse(noContent().build());
	}

	@GetMapping("/textures/{hash:[a-f0-9]{64}}")
	public ResponseEntity<?> texture(@PathVariable String hash) {
		return mysqlDatabase.getTexturesStorage().getTexture(hash)
				.map(texture -> ok()
						.contentType(IMAGE_PNG)
						.eTag(texture.hash)
						.cacheControl(maxAge(30, DAYS).cachePublic())
						.body(texture.data))
				.orElse(notFound().build());
	}

	@DeleteMapping("/api/user/profile/{uuid}/{textureType}")
	public ResponseEntity<?> deleteTexture(@PathVariable String uuid, @PathVariable TextureType textureType, @RequestHeader(required = false) String authorization) {
		var character = authTextureOperation(uuid, textureType, authorization);
		return noContent().build();
	}

	@PutMapping("/api/user/profile/{uuid}/{textureType}")
	public ResponseEntity<?> uploadTexture(@PathVariable String uuid, @PathVariable TextureType textureType, @RequestHeader(required = false) String authorization,
			@RequestPart("file") byte[] imageFile,
			@RequestPart(name = "model", required = false) String textureModel) {
		var character = authTextureOperation(uuid, textureType, authorization);
		Texture texture;
		try (var in = new ByteArrayInputStream(imageFile)) {
			texture = mysqlDatabase.getTexturesStorage().loadTexture(in);
		} catch (IOException e) {
			logger.warn("unable to parse uploaded texture", e);
			throw newIllegalArgumentException("bad image");
		}
		if (textureType == TextureType.SKIN) {
			if ("slim".equals(textureModel)) {
				character.setModel(ModelType.ALEX);
			} else {
				character.setModel(ModelType.STEVE);
			}
		}

		mysqlDatabase.getTexturesStorage().uploadTexture(texture);
		mysqlDatabase.setTexture(toUUID(uuid), texture, textureType);
		return noContent().build();
	}

	@ExceptionHandler(ValidationException.class)
	public void onMalformedRequest(ValidationException e) {
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, null, e);
	}

	// ---- Helper methods ----
	private YggdrasilUser passwordAuthenticated(String username, String password) {
		var user = mysqlDatabase.findUserByEmail(username)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_credentials));

		if (!rateLimiter.tryAccess(user))
			throw newForbiddenOperationException(m_invalid_credentials);

		if (!EncryptUtils.verifyArgon2Hash(user.getPassword(), password)) {
			throw newForbiddenOperationException(m_invalid_credentials);
		}

		return user;
	}

	private Token authenticate(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel) {
		return tokenStore.authenticate(accessToken, clientToken, availableLevel)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_token));
	}

	private Token authenticateAndConsume(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel, Predicate<Token> checker) {
		return tokenStore.authenticateAndConsume(accessToken, clientToken, availableLevel, checker)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_token));
	}

	private Token processAuthorizationHeader(String header) {
		if (header != null) {
			header = header.trim();
			if (header.startsWith("Bearer ")) {
				header = header.substring("Bearer ".length());
				var token = tokenStore.authenticate(header, null, AvailableLevel.COMPLETE);
				if (token.isPresent()) {
					return token.get();
				}
			}
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
	}

	private YggdrasilCharacter authTextureOperation(String uuid, TextureType textureType, String authorization) {
		var token = processAuthorizationHeader(authorization);
		var character = mysqlDatabase.findCharacterByUUID(toUUID(uuid))
				.orElseThrow(() -> newIllegalArgumentException(m_profile_not_found));
		if (character.getOwner() != token.getUser())
			throw newForbiddenOperationException(m_access_denied);
		if (!character.getUploadableTextures().contains(textureType))
			throw newForbiddenOperationException(m_access_denied);
		return character;
	}
	// --------

	// ---- Requests ----
	public static class LoginRequest {
		public @NotBlank String username;
		public @NotBlank String password;
		public String clientToken;
		public boolean requestUser = false;
	}

	public static class RefreshRequest {
		public @NotBlank String accessToken;
		public String clientToken;
		public boolean requestUser = false;
		public ProfileBody selectedProfile;
	}

	public static class ProfileBody {
		public @NotBlank String id;
		public @NotBlank String name;
	}

	public static class ValidateRequest {
		public @NotBlank String accessToken;
		public String clientToken;
	}

	public static class InvalidateRequest {
		public @NotBlank String accessToken;
	}

	public static class SignoutRequest {
		public @NotBlank String username;
		public @NotBlank String password;
	}

	public static class JoinServerRequest {
		public @NotBlank String accessToken;
		public @NotBlank String selectedProfile;
		public @NotBlank String serverId;
	}
	// --------

}
