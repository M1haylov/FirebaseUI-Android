/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firebase.ui.auth.ui.idp;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.util.Log;

import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.ResultCodes;
import com.firebase.ui.auth.provider.ProviderUtils;
import com.firebase.ui.auth.ui.BaseHelper;
import com.firebase.ui.auth.ui.User;
import com.firebase.ui.auth.ui.accountlink.AccountLinker;
import com.firebase.ui.auth.ui.accountlink.WelcomeBackIdpPrompt;
import com.firebase.ui.auth.ui.accountlink.WelcomeBackPasswordPrompt;
import com.firebase.ui.auth.util.signincontainer.SaveSmartLock;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CredentialSignInHandler implements OnCompleteListener<AuthResult> {
    private static final String TAG = "CredentialSignInHandler";

    private Activity mActivity;
    private BaseHelper mHelper;
    @Nullable
    private SaveSmartLock mSmartLock;
    private IdpResponse mResponse;
    private int mAccountLinkRequestCode;

    public CredentialSignInHandler(
            Activity activity,
            BaseHelper helper,
            @Nullable SaveSmartLock smartLock,
            int accountLinkRequestCode,
            IdpResponse response) {
        mActivity = activity;
        mHelper = helper;
        mSmartLock = smartLock;
        mResponse = response;
        mAccountLinkRequestCode = accountLinkRequestCode;
    }

    @Override
    public void onComplete(@NonNull Task<AuthResult> task) {
        if (task.isSuccessful()) {
            FirebaseUser firebaseUser = task.getResult().getUser();
            mHelper.saveCredentialsOrFinish(
                    mSmartLock,
                    mActivity,
                    firebaseUser,
                    null,
                    mResponse);
        } else {
            if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                String email = mResponse.getEmail();
                if (email != null) {
                    ProviderUtils.fetchTopProvider(mHelper.getFirebaseAuth(), email)
                            .addOnSuccessListener(new StartWelcomeBackFlow())
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    mHelper.finishActivity(
                                            mActivity,
                                            ResultCodes.CANCELED,
                                            IdpResponse.getErrorCodeIntent(ErrorCodes.UNKNOWN_ERROR));
                                }
                            });
                    return;
                }
            }
            Log.e(TAG,
                  "Unexpected exception when signing in with credential "
                          + mResponse.getProviderType()
                          + " unsuccessful. Visit https://console.firebase.google.com to enable it.",
                  task.getException());

            mHelper.dismissDialog();
        }
    }

    private class StartWelcomeBackFlow implements OnSuccessListener<String> {
        @Override
        public void onSuccess(String provider) {
            AuthCredential credential = ProviderUtils.getAuthCredential(mResponse);
            if (mHelper.canLinkAccounts()
                    && credential != null
                    && provider.equals(credential.getProvider())) {
                // We don't want to show the welcome back dialog since the user selected
                // an existing account and we can just link the two accounts without knowing
                // prevCredential.
                AccountLinker.linkWithCurrentUser(mActivity, mHelper, mResponse, credential);
                return;
            }
            mHelper.dismissDialog();

            if (provider == null) {
                throw new IllegalStateException(
                        "No provider even though we received a FirebaseAuthUserCollisionException");
            } else if (provider.equals(EmailAuthProvider.PROVIDER_ID)) {
                // Start email welcome back flow
                mActivity.startActivityForResult(
                        WelcomeBackPasswordPrompt.createIntent(
                                mActivity,
                                mHelper.getFlowParams(),
                                mResponse),
                        mAccountLinkRequestCode);
            } else {
                // Start Idp welcome back flow
                mActivity.startActivityForResult(
                        WelcomeBackIdpPrompt.createIntent(
                                mActivity,
                                mHelper.getFlowParams(),
                                new User.Builder(mResponse.getEmail())
                                        .setProvider(provider)
                                        .build(),
                                mResponse),
                        mAccountLinkRequestCode);
            }
        }
    }
}
