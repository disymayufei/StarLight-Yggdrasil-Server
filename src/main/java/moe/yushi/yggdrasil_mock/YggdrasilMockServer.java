package moe.yushi.yggdrasil_mock;

import moe.yushi.yggdrasil_mock.utils.secure.EncryptUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.function.Supplier;

import static java.text.MessageFormat.format;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static moe.yushi.yggdrasil_mock.utils.PropertiesUtils.getSignaturePublicKey;

@Configuration
@ConfigurationProperties(prefix = "yggdrasil.core")
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class YggdrasilMockServer {

	private List<String> skinDomains;
	private String url;
	private String serverName;
	private boolean loginWithCharacterName;

	@Bean
	public String publickeyPem() {
		return EncryptUtils.toPEMPublicKey(getSignaturePublicKey());
	}

	@Bean
	public ServerMeta serverMeta(
			@Value("#{publickeyPem}") String publickeyPem,
			@Value("${build.name}") String buildName,
			@Value("${git.commit.id}") String gitCommit) {
		var meta = new ServerMeta();
		meta.setSignaturePublickey(publickeyPem);
		meta.setSkinDomains(skinDomains);
		meta.setMeta(ofEntries(
				entry("serverName", serverName),
				entry("implementationName", buildName),
				entry("implementationVersion", format("git-" + gitCommit.substring(0, 7))),
				entry("feature.non_email_login", loginWithCharacterName)));
		return meta;
	}

	@Bean
	public Supplier<UriBuilder> rootUrl() {
		return () -> UriComponentsBuilder.fromHttpUrl(url);
	}

	public List<String> getSkinDomains() {
		return skinDomains;
	}

	public void setSkinDomains(List<String> skinDomains) {
		this.skinDomains = skinDomains;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getServerName() {
		return serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public boolean isLoginWithCharacterName() {
		return loginWithCharacterName;
	}

	public void setLoginWithCharacterName(boolean loginWithCharacterName) {
		this.loginWithCharacterName = loginWithCharacterName;
	}
}
