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

package com.firebase.uidemo.database;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.adapter.FirebaseIndexRecyclerAdapter;
import com.firebase.ui.database.adapter.FirebaseRecyclerAdapter;
import com.firebase.uidemo.R;
import com.firebase.uidemo.util.SignInResultNotifier;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ChatActivity extends AppCompatActivity implements FirebaseAuth.AuthStateListener {
    private static final String TAG = "RecyclerViewDemo";

    private FirebaseAuth mAuth;
    private DatabaseReference mChatIndicesRef;
    private DatabaseReference mChatRef;
    private Button mSendButton;
    private EditText mMessageEdit;

    private RecyclerView mMessages;
    private LinearLayoutManager mManager;
    private FirebaseRecyclerAdapter<Chat, ChatHolder> mAdapter;
    private TextView mEmptyListMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mAuth = FirebaseAuth.getInstance();
        mAuth.addAuthStateListener(this);

        mSendButton = (Button) findViewById(R.id.sendButton);
        mMessageEdit = (EditText) findViewById(R.id.messageEdit);
        mEmptyListMessage = (TextView) findViewById(R.id.emptyTextView);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference();
        mChatIndicesRef = ref.child("chatIndices");
        mChatRef = ref.child("chats");

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String uid = mAuth.getCurrentUser().getUid();
                String name = "User " + uid.substring(0, 6);

                Chat chat = new Chat(name, mMessageEdit.getText().toString(), uid);
                DatabaseReference chatRef = mChatRef.push();
                mChatIndicesRef.child(chatRef.getKey()).setValue(true);
                chatRef.setValue(chat, new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError error, DatabaseReference reference) {
                        if (error != null) {
                            Log.e(TAG, "Failed to write message", error.toException());
                        }
                    }
                });

                mMessageEdit.setText("");
            }
        });

        mManager = new LinearLayoutManager(this);
        mManager.setReverseLayout(false);

        mMessages = (RecyclerView) findViewById(R.id.messagesList);
        mMessages.setHasFixedSize(false);
        mMessages.setLayoutManager(mManager);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Default Database rules do not allow unauthenticated reads, so we need to
        // sign in before attaching the RecyclerView adapter otherwise the Adapter will
        // not be able to read any data from the Database.
        if (isSignedIn()) {
            attachRecyclerViewAdapter();
        } else {
            signInAnonymously();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAdapter != null) {
            mAdapter.cleanup();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAuth != null) {
            mAuth.removeAuthStateListener(this);
        }
    }

    @Override
    public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
        updateUI();
    }

    private void attachRecyclerViewAdapter() {
        mAdapter = new FirebaseIndexRecyclerAdapter<Chat, ChatHolder>(
                Chat.class,
                R.layout.message,
                ChatHolder.class,
                mChatIndicesRef.limitToLast(50),
                mChatRef) {
            @Override
            public void populateViewHolder(final ChatHolder holder, Chat chat, int position) {
                holder.bind(chat);

                holder.itemView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                    @Override
                    public void onCreateContextMenu(ContextMenu menu,
                                                    View v,
                                                    ContextMenu.ContextMenuInfo menuInfo) {
                        menu.add("Delete")
                                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem item) {
                                        DatabaseReference ref = getRef(holder.getAdapterPosition());
                                        mChatIndicesRef.child(ref.getKey()).removeValue();
                                        ref.removeValue();
                                        return true;
                                    }
                                });
                    }
                });
            }

            @Override
            public void onChildChanged(EventType type, int index, int oldIndex) {
                super.onChildChanged(type, index, oldIndex);

                // TODO temporary fix for https://github.com/firebase/FirebaseUI-Android/issues/546
                onDataChanged();
            }

            @Override
            public void onDataChanged() {
                // If there are no chat messages, show a view that invites the user to add a message.
                mEmptyListMessage.setVisibility(getItemCount() == 0 ? View.VISIBLE : View.GONE);
            }
        };

        // Scroll to bottom on new messages
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                mManager.smoothScrollToPosition(mMessages, null, mAdapter.getItemCount());
            }
        });

        mMessages.setAdapter(mAdapter);
    }

    private void signInAnonymously() {
        Toast.makeText(this, "Signing in...", Toast.LENGTH_SHORT).show();
        mAuth.signInAnonymously()
                .addOnSuccessListener(this, new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult result) {
                        attachRecyclerViewAdapter();
                    }
                })
                .addOnCompleteListener(new SignInResultNotifier(this));
    }

    private boolean isSignedIn() {
        return mAuth.getCurrentUser() != null;
    }

    private void updateUI() {
        // Sending only allowed when signed in
        mSendButton.setEnabled(isSignedIn());
        mMessageEdit.setEnabled(isSignedIn());
    }
}
