package io.jenkins.plugins.eks_token_plugin;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsSessionRule;

import java.util.Collections;
import java.util.Optional;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static io.jenkins.plugins.eks_token_plugin.EksTokenCredentials.EKS_TOKEN_CREDENTIALS_DISPLAY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

public class EksTokenCredentialsUITest {

    public static final String CREDENTIALS_FIELD_XPATH_PREFIX = "//div[(@class='tr form-group') and not (@field-disabled='true')]//";
    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Test
    public void testGenerateEksToken() throws Throwable {
        sessions.then((r) -> {
            SystemCredentialsProvider systemCredentialsProvider = SystemCredentialsProvider.getInstance();
            AWSCredentialsImpl awsCredentials = new AWSCredentialsImpl(GLOBAL, "aws1", "accessKey1", "secret1", "aws1");
            systemCredentialsProvider.getCredentials().add(awsCredentials);
            JenkinsRule.WebClient webClient = r.createWebClient();
            HtmlPage page = webClient.goTo("credentials/store/system/domain/_/newCredentials");
            HtmlForm newCredentialsForm = page.getFormByName("newCredentials");
            assertThat(newCredentialsForm).isNotNull();
            HtmlSelect credentials = newCredentialsForm.getFirstByXPath("div[1]/div[1]/div[2]/select");
            assertThat(credentials).isNotNull();
            credentials.getOptionByText(EKS_TOKEN_CREDENTIALS_DISPLAY_NAME).setSelected(true);
            webClient.waitForBackgroundJavaScript(5000);
            HtmlTextInput idInput = newCredentialsForm.getFirstByXPath(CREDENTIALS_FIELD_XPATH_PREFIX + "input[(@name=\"_.id\")]");
            idInput.setText("eks1");
            HtmlTextInput descriptionInput = newCredentialsForm.getFirstByXPath(CREDENTIALS_FIELD_XPATH_PREFIX + "input[(@name=\"_.description\")]");
            descriptionInput.setText("eks1 desc");
            HtmlTextInput clusterNameInput = newCredentialsForm.getFirstByXPath(CREDENTIALS_FIELD_XPATH_PREFIX + "input[(@name=\"_.clusterName\")]");
            clusterNameInput.setText("eks1");
            HtmlSelect region = newCredentialsForm.getFirstByXPath(CREDENTIALS_FIELD_XPATH_PREFIX + "select[(@name=\"_.region\")]");
            region.getOptionByText("us-west-2").setSelected(true);
            HtmlSelect awsCredentialsSelect = newCredentialsForm.getFirstByXPath(CREDENTIALS_FIELD_XPATH_PREFIX + "select[(@name=\"_.awsCredentialsId\")]");
            awsCredentialsSelect.getOptionByValue("aws1").setSelected(true);
            r.submit(newCredentialsForm);
        });

        sessions.then((r) -> {
            Optional<EksTokenCredentials> eksTokenCredentialsOptional = lookupCredentials(EksTokenCredentials.class,
                    Jenkins.getInstanceOrNull(), ACL.SYSTEM, Collections.emptyList())
                    .stream()
                    .filter(CredentialsMatchers.withId("eks1")::matches)
                    .findFirst();
            assertThat(eksTokenCredentialsOptional).isPresent();
            EksTokenCredentials eksTokenCredentials = eksTokenCredentialsOptional.get();
            assertThat(eksTokenCredentials.getId()).isEqualTo("eks1");
            assertThat(eksTokenCredentials.getDescription()).isEqualTo("eks1 desc");
            assertThat(eksTokenCredentials.getClusterName()).isEqualTo("eks1");
            assertThat(eksTokenCredentials.getRegion()).isEqualTo("us-west-2");
            assertThat(eksTokenCredentials.getAwsCredentialsId()).isEqualTo("aws1");

        });
    }
}
