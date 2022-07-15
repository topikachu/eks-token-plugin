package io.jenkins.plugins.eks_token_plugin;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.cloudbees.plugins.credentials.CredentialsScope.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class EksTokenCredentialsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGenerateEksToken() throws MalformedURLException {
        SystemCredentialsProvider systemCredentialsProvider  = SystemCredentialsProvider.getInstance();
        EksTokenCredentials eksTokenCredentials = spy(new EksTokenCredentials(SYSTEM, "eks1", "eks1", "eks1", Regions.US_WEST_2.getName()));
        AWSCredentialsProvider mockAwsCredentialsProvider = mock(AWSCredentialsProvider.class);

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials("accessKey1", "secretKey1");
        when(mockAwsCredentialsProvider.getCredentials())
                .thenReturn(awsCredentials);
        when(eksTokenCredentials.getAwsCredentialsProvider())
                .thenReturn(mockAwsCredentialsProvider);
        systemCredentialsProvider.getCredentials().add(eksTokenCredentials);
        String token = eksTokenCredentials.getSecret().getPlainText();
        assertThat(token).startsWith("k8s-aws-v1.");
        String encodedUrl = token.substring("k8s-aws-v1.".length());
        URL url = new URL(new String(Base64.getUrlDecoder().decode(encodedUrl), StandardCharsets.UTF_8));

        assertThat(url)
                .hasProtocol("https")
                .hasHost("sts.us-west-2.amazonaws.com")
                .hasPath("/")
                .hasParameter("Action", "GetCallerIdentity")
                .hasParameter("Version", "2011-06-15")
                .hasParameter("X-Amz-Algorithm", "AWS4-HMAC-SHA256")
                .hasParameter("X-Amz-Date")
                .hasParameter("X-Amz-SignedHeaders", "host;x-k8s-aws-id")
                .hasParameter("X-Amz-Expires")
                .hasParameter("X-Amz-Credential")
                .hasParameter("X-Amz-Signature");

    }
}
