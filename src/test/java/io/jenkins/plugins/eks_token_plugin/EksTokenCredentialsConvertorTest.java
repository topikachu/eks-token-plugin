package io.jenkins.plugins.eks_token_plugin;


import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.CredentialsConvertionException;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

public class EksTokenCredentialsConvertorTest {
    @Test
    public void canConvert() {
        EksTokenCredentialsConvertor convertor = new EksTokenCredentialsConvertor();
        assertThat(convertor.canConvert("eks")).isTrue();
        assertThat(convertor.canConvert("something")).isFalse();
    }

    @Test
    public void canConvertAValidSecret() throws CredentialsConvertionException, IOException {
        EksTokenCredentialsConvertor convertor = new EksTokenCredentialsConvertor();
        try (InputStream is = get("valid.yaml")) {
            Secret secret = Serialization.unmarshal(is, Secret.class);
            assertThat(secret).isNotNull();
            EksTokenCredentials credential = convertor.convert(secret);
            assertThat(credential).isNotNull();
            assertThat(credential.getId()).isEqualTo("a-test-eks");
            assertThat(credential.getDescription()).isEqualTo("eks1 token");
            assertThat(credential.getRegion()).isEqualTo("us-west-2");
            assertThat(credential.getClusterName()).isEqualTo("eks1");
            assertThat(credential.getAwsCredentialsId()).isEqualTo("aws-1");


        }
    }

    private static InputStream get(String resource) {
        InputStream is = EksTokenCredentialsConvertor.class.getResourceAsStream("EksTokenCredentialsConvertor/" + resource);
        if (is == null) {
            fail("failed to load resource " + resource);
        }
        return is;
    }
}

