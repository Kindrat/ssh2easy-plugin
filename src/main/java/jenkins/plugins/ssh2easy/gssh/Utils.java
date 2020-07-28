package jenkins.plugins.ssh2easy.gssh;

import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Utils {

    public static String getStringFromStream(@Nullable InputStream is) {
        if (null == is) {
            throw new RuntimeException("Convert Stream to String failed as input stream is null");
        }
        try {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Convert Stream to String failed !", e);
        }
    }
}
