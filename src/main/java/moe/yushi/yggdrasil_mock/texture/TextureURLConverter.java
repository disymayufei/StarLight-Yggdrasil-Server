package moe.yushi.yggdrasil_mock.texture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;

@Component
@ConfigurationPropertiesBinding
public class TextureURLConverter implements Converter<String, Texture> {

	private @Autowired Texture.Storage storage;

	@Override
	public Texture convert(String source) {
		try {
			return storage.loadTexture(source);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
