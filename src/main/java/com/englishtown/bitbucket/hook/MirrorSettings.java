package com.englishtown.bitbucket.hook;

import java.io.Serializable;

class MirrorSettings implements Serializable {

    String mirrorRepoUrl;
    String username;
    String password;
    String suffix;
    String refspec;
    String refspecNoForce;
    boolean tags;
    boolean notes;
    boolean atomic;
}
