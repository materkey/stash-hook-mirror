package com.englishtown.bitbucket.hook;

import com.atlassian.bitbucket.scm.CommandErrorHandler;
import com.atlassian.bitbucket.scm.CommandExitHandler;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.bitbucket.io.StringOutputHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Handles removing passwords from output text
 */
class PasswordHandler extends StringOutputHandler
        implements CommandOutputHandler<String>, CommandErrorHandler, CommandExitHandler {

    private final String target;
    private final CommandExitHandler exitHandler;

    private static final Logger log = LoggerFactory.getLogger(PasswordHandler.class);

    private static final String PASSWORD_REPLACEMENT = ":*****@";

    public PasswordHandler(String password, CommandExitHandler exitHandler) {
        this.exitHandler = exitHandler;
        this.target = ":" + password + "@";
    }

    public String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace(target, PASSWORD_REPLACEMENT);
    }

    @Override
    public String getOutput() {
        return cleanText(super.getOutput());
    }

    @Override
    public void onCancel(@Nonnull String command, int exitCode, @Nullable String stdErr, @Nullable Throwable thrown) {
        log.error(getOutput());
        exitHandler.onCancel(cleanText(command), exitCode, cleanText(stdErr), thrown);
    }

    @Override
    public void onExit(@Nonnull String command, int exitCode, @Nullable String stdErr, @Nullable Throwable thrown) {
        log.error(getOutput());
        exitHandler.onExit(cleanText(command), exitCode, cleanText(stdErr), thrown);
    }

}

