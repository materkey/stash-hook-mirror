package com.englishtown.bitbucket.hook;

import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.i18n.SimpleI18nService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.scm.ScmService;
import com.atlassian.bitbucket.scm.git.command.GitCommand;
import com.atlassian.bitbucket.scm.git.command.GitScmCommandBuilder;
import com.atlassian.bitbucket.server.ApplicationPropertiesService;
import com.atlassian.bitbucket.user.DummySecurityService;
import com.atlassian.bitbucket.user.SecurityService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static com.atlassian.bitbucket.mockito.MockitoUtils.returnArg;
import static com.atlassian.bitbucket.mockito.MockitoUtils.returnFirst;
import static com.atlassian.bitbucket.mockito.MockitoUtils.returnsSelf;
import static com.englishtown.bitbucket.hook.MirrorBucketProcessor.PROP_TIMEOUT;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MirrorBucketProcessor}.
 */
public class MirrorBucketProcessorTest {

    private static final String URL_HTTP = "https://bitbucket-mirror.englishtown.com/scm/test/test.git";
    private static final String URL_SSH = "ssh://git@bitbucket-mirror.englishtown.com/scm/test/test.git";

    private static final MirrorSettings SETTINGS = new MirrorSettings() {
        {
            mirrorRepoUrl = URL_SSH;
            password = "test-password";
            refspec = "+refs/heads/master:refs/heads/master +refs/heads/develop:refs/heads/develop";
            refspecNoForce = "";
            username = "test-user";

            atomic = true;
            notes = true;
            tags = true;
        }
    };
    private static final MirrorRequest REQUEST = new MirrorRequest(1, SETTINGS);
    private static final List<MirrorRequest> REQUESTS = Collections.singletonList(REQUEST);

    private static final MirrorSettings SETTINGS_WITH_NO_FORCE = new MirrorSettings() {
        {
            mirrorRepoUrl = URL_SSH;
            password = "test-password";
            refspec = "";
            refspecNoForce = "+refs/heads/master:refs/heads/master +refs/heads/develop:refs/heads/develop";
            username = "test-user";

            atomic = true;
            notes = true;
            tags = true;
        }
    };
    private static final MirrorRequest REQUEST_WITH_NO_FORCE = new MirrorRequest(1, SETTINGS_WITH_NO_FORCE);
    private static final List<MirrorRequest> REQUESTS_WITH_NO_FORCE = Collections.singletonList(REQUEST_WITH_NO_FORCE);

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule().silent();

    @Mock
    private GitScmCommandBuilder builder;

    private boolean isFirstBuilderCreated = false;

    @Mock
    private GitScmCommandBuilder builderNoForce;
    @Mock
    private GitCommand<String> command;
    @Spy
    private I18nService i18nService = new SimpleI18nService();
    @Mock
    private PasswordEncryptor passwordEncryptor;
    private MirrorBucketProcessor processor;
    @Mock
    private ApplicationPropertiesService propertiesService;
    @Mock
    private Repository repository;
    @Mock
    private ScmService scmService;
    @Mock
    private RepositoryService repositoryService;
    @Spy
    private SecurityService securityService = new DummySecurityService();

    @Before
    public void setup() {
        when(builder.command(anyString())).thenAnswer(returnsSelf());
        when(builder.argument(anyString())).thenAnswer(returnsSelf());
        when(builder.errorHandler(any())).thenAnswer(returnsSelf());
        when(builder.exitHandler(any())).thenAnswer(returnsSelf());
        when(builder.<String>build(any())).thenReturn(command);

        when(builderNoForce.command(anyString())).thenAnswer(returnsSelf());
        when(builderNoForce.argument(anyString())).thenAnswer(returnsSelf());
        when(builderNoForce.errorHandler(any())).thenAnswer(returnsSelf());
        when(builderNoForce.exitHandler(any())).thenAnswer(returnsSelf());
        when(builderNoForce.<String>build(any())).thenReturn(command);

        when(passwordEncryptor.decrypt(anyString())).thenAnswer(returnFirst());
        when(propertiesService.getPluginProperty(eq(PROP_TIMEOUT), anyLong())).thenAnswer(returnArg(1));

        // Return builder for first call to createBuilder, then always return builderNoForce
        doAnswer(invocation -> {
            if (isFirstBuilderCreated) {
                return builderNoForce;
            }
            isFirstBuilderCreated = true;
            return builder;
        }).when(scmService).createBuilder(any());

        processor = new MirrorBucketProcessor(i18nService, passwordEncryptor,
                propertiesService, repositoryService, scmService, securityService);
    }

    @Test
    public void testProcess() {
        when(repositoryService.getById(eq(1))).thenReturn(repository);

        processor.process("ignored", REQUESTS);

        verify(builder).command(eq("push"));
        verify(builder).argument(eq("--prune"));
        verify(builder).argument(eq("--force"));
        verify(builder).argument(eq(URL_SSH));
        verify(builder).argument(eq("--atomic"));
        verify(builder).argument(eq("+refs/heads/master:refs/heads/master"));
        verify(builder).argument(eq("+refs/heads/develop:refs/heads/develop"));
        verify(builder).argument(eq("+refs/tags/*:refs/tags/*"));
        verify(builder).argument(eq("+refs/notes/*:refs/notes/*"));
        verify(command).call();
        verify(command).setTimeout(eq(Duration.ofSeconds(120L)));
        verify(passwordEncryptor).decrypt(eq(SETTINGS.password));
        verify(scmService).createBuilder(same(repository));

        verify(builderNoForce, never()).command(anyString());
    }

    @Test
    public void testProcessWithNoForce() {
        when(repositoryService.getById(eq(1))).thenReturn(repository);

        processor.process("ignored", REQUESTS_WITH_NO_FORCE);

        verify(builder).command(eq("push"));
        verify(builder).argument(eq("--prune"));
        verify(builder).argument(eq("--force"));
        verify(builder).argument(eq(URL_SSH));
        verify(builder).argument(eq("--atomic"));
        verify(builder).argument(eq("+refs/tags/*:refs/tags/*"));
        verify(builder).argument(eq("+refs/notes/*:refs/notes/*"));
        // called twice
        verify(command, times(2)).call();

        verify(command, times(2)).setTimeout(eq(Duration.ofSeconds(120L)));
        verify(passwordEncryptor, times(2)).decrypt(eq(SETTINGS.password));
        verify(scmService, times(2)).createBuilder(same(repository));

        verify(builderNoForce).command(eq("push"));
        verify(builderNoForce).argument(eq("--prune"));
        verify(builderNoForce, never()).argument(eq("--force"));
        verify(builderNoForce).argument(eq(URL_SSH));
        verify(builderNoForce).argument(eq("--atomic"));
        verify(builderNoForce).argument(eq("+refs/heads/master:refs/heads/master"));
        verify(builderNoForce).argument(eq("+refs/heads/develop:refs/heads/develop"));
        verify(builderNoForce, never()).argument(eq("+refs/tags/*:refs/tags/*"));
        verify(builderNoForce, never()).argument(eq("+refs/notes/*:refs/notes/*"));
    }

    @Test
    public void testProcessWithDeletedRepository() {
        processor.process("ignored", REQUESTS);

        verify(repositoryService).getById(eq(1));
        verifyNoMoreInteractions(repositoryService);
        verifyZeroInteractions(scmService);
    }

    @Test
    public void testProcessWithEmptyRepository() {
        when(repositoryService.getById(eq(1))).thenReturn(repository);
        when(repositoryService.isEmpty(same(repository))).thenReturn(true);

        processor.process("ignored", REQUESTS);

        verify(repositoryService).getById(eq(1));
        verify(repositoryService).isEmpty(same(repository));
        verifyZeroInteractions(passwordEncryptor, scmService);
    }

    @Test
    public void testProcessWithoutRequests() {
        processor.process("ignored", Collections.emptyList());

        verifyZeroInteractions(repositoryService, scmService);
    }

    @Test
    public void testGetAuthenticatedUrlForHttp() {
        String url = processor.getAuthenticatedUrl(URL_HTTP, "user", "password");
        assertEquals("https://user:password@bitbucket-mirror.englishtown.com/scm/test/test.git", url);
    }

    @Test
    public void testGetAuthenticatedUrlForSsh() {
        String url = processor.getAuthenticatedUrl(URL_SSH, "user", "password");
        assertEquals(URL_SSH, url);
    }
}
