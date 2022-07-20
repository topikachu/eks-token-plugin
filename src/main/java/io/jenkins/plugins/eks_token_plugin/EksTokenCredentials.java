package io.jenkins.plugins.eks_token_plugin;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.*;
import com.amazonaws.auth.presign.PresignerFacade;
import com.amazonaws.auth.presign.PresignerParams;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.internal.auth.DefaultSignerProvider;
import com.amazonaws.internal.auth.SignerProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Log
public class EksTokenCredentials extends BaseStandardCredentials
        implements StringCredentials {

    public static final String EKS_TOKEN_CREDENTIALS_DISPLAY_NAME = "EKS Token Credentials";
    @Setter
    @Getter
    private String clusterName;
    @Setter
    @Getter
    private String region;

    @DataBoundSetter
    @Setter
    @Getter
    private String awsCredentialsId;

    @DataBoundConstructor
    @SuppressWarnings("unused") // b
    public EksTokenCredentials(CredentialsScope scope, String id, String description,
                               @NonNull String clusterName,
                               @NonNull String region
    ) {
        super(scope, id, description);
        this.clusterName = clusterName;
        this.region = region;
    }


    @NonNull
    @Override
    public Secret getSecret() {

        AWSCredentialsProvider credentialsProvider = getAwsCredentialsProvider();
        String token = generateEksToken(clusterName, region, credentialsProvider);
        return Secret.fromString(token);

    }

    AWSCredentialsProvider getAwsCredentialsProvider() {
        AWSCredentialsProvider credentialsProvider = Optional.ofNullable(awsCredentialsId)
                .filter(StringUtils::isNotEmpty)
                .flatMap(AwsCredentialsHelper::getAWSCredentials)
                .map(AWSStaticCredentialsProvider::new)
                .map(AWSCredentialsProvider.class::cast)
                .orElseGet(DefaultAWSCredentialsProviderChain::new);
        return credentialsProvider;
    }

    @SneakyThrows
    public String generateEksToken(String clusterName, String region, AWSCredentialsProvider credentialsProvider) {
        String host = Region.getRegion(Regions.fromName(region)).getServiceEndpoint(AWSSecurityTokenService.ENDPOINT_PREFIX);
        DefaultRequest defaultRequest = new DefaultRequest<>(new GetCallerIdentityRequest(), "sts");
        URI uri = new URI("https", host, null, null);
        defaultRequest.setResourcePath("/");
        defaultRequest.setEndpoint(uri);
        defaultRequest.setHttpMethod(HttpMethodName.GET);
        defaultRequest.addParameter("Action", "GetCallerIdentity");
        defaultRequest.addParameter("Version", "2011-06-15");
        defaultRequest.addHeader("x-k8s-aws-id", clusterName);
        Signer signer = SignerFactory.createSigner(SignerFactory.VERSION_FOUR_SIGNER,
                new SignerParams("sts", region));
        AWSSecurityTokenServiceClient stsClient = (AWSSecurityTokenServiceClient) AWSSecurityTokenServiceClientBuilder
                .standard().withRegion(region).withCredentials(credentialsProvider).build();
        SignerProvider signerProvider = new DefaultSignerProvider(stsClient, signer);
        PresignerParams presignerParams = new PresignerParams(uri, credentialsProvider, signerProvider,
                SdkClock.STANDARD);
        PresignerFacade presignerFacade = new PresignerFacade(presignerParams);
        URL url = presignerFacade.presign(defaultRequest, new Date(System.currentTimeMillis() + 60000));
        String encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toString().getBytes(StandardCharsets.UTF_8));
        return "k8s-aws-v1." + encodedUrl;

    }


    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return EKS_TOKEN_CREDENTIALS_DISPLAY_NAME;
        }


        public ListBoxModel doFillAwsCredentialsIdItems() {
            Jenkins.get().checkPermission(Jenkins.SYSTEM_READ); // or Jenkins.getInstance() on older core baselines
            StandardListBoxModel result = new StandardListBoxModel();

            return result
                    .includeEmptyValue() // (3)
                    .includeMatchingAs(ACL.SYSTEM,
                            Jenkins.get(),
                            AmazonWebServicesCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(
                                    AmazonWebServicesCredentials.class));// (4)
        }

        @POST
        public FormValidation doCheckClusterName(@QueryParameter String clusterName) {
            if (StringUtils.isEmpty(clusterName)) {
                return FormValidation.error("Cluster Name is Empty");
            } else {
                return FormValidation.ok();
            }
        }

        public ListBoxModel doFillRegionItems(@AncestorInPath Item item) {
            ListBoxModel result = new ListBoxModel();

            Arrays.stream(Regions.values())
                    .forEach(
                            region -> result.add(region.getName())
                    );

            return result;
        }


    }
}
