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

package com.firebase.ui.database;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * This class implements a collection on top of a Firebase location.
 */
public class FirebaseArray implements ChildEventListener, ValueEventListener, List<DataSnapshot> {
    private Query mQuery;
    private boolean mNotifyListeners = true;
    private List<ChangeEventListener> mListeners = new ArrayList<>();
    private List<DataSnapshot> mSnapshots = new ArrayList<>();

    public FirebaseArray(Query query) {
        mQuery = query;
    }

    /**
     * Add a listener for change events and errors occurring at the location provided in {@link
     * #FirebaseArray(Query)}.
     *
     * @param listener the listener to be called with changes
     * @return a reference to the listener provided. Save this to remove the listener later
     * @throws IllegalArgumentException if the listener is null
     */
    public ChangeEventListener addChangeEventListener(@NonNull ChangeEventListener listener) {
        checkNotNull(listener);

        mListeners.add(listener);
        if (mListeners.size() <= 1) {
            mQuery.addChildEventListener(this);
            mQuery.addValueEventListener(this);
        }

        return listener;
    }

    /**
     * Remove the {@link ChangeEventListener} from the location provided in {@link
     * #FirebaseArray(Query)}. The list will be empty after this call returns.
     *
     * @param listener the listener to remove
     * @throws IllegalArgumentException if the listener is null
     */
    public void removeChangeEventListener(@NonNull ChangeEventListener listener) {
        checkNotNull(listener);

        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            mQuery.removeEventListener((ValueEventListener) this);
            mQuery.removeEventListener((ChildEventListener) this);
            mSnapshots.clear();
        }
    }

    private static void checkNotNull(ChangeEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("ChangeEventListener cannot be null.");
        }
    }

    /**
     * @return true if {@link FirebaseArray} is listening for change events, false otherwise
     */
    public boolean isListening() {
        return mListeners.size() > 0;
    }

    protected void setShouldNotifyListeners(boolean notifyListeners) {
        mNotifyListeners = notifyListeners;
    }

    protected void notifyChangeEventListeners(ChangeEventListener.EventType type, int index) {
        notifyChangeEventListeners(type, index, -1);
    }

    protected void notifyChangeEventListeners(ChangeEventListener.EventType type,
                                              int index,
                                              int oldIndex) {
        if (!mNotifyListeners) return;
        for (ChangeEventListener listener : mListeners) {
            listener.onChildChanged(type, index, oldIndex);
        }
    }

    protected void notifyListenersOnDataChanged() {
        if (!mNotifyListeners) return;
        for (ChangeEventListener listener : mListeners) {
            listener.onDataChanged();
        }
    }

    protected void notifyListenersOnCancelled(DatabaseError error) {
        if (!mNotifyListeners) return;
        for (ChangeEventListener listener : mListeners) {
            listener.onCancelled(error);
        }
    }

    @Override
    public void onChildAdded(DataSnapshot snapshot, String previousChildKey) {
        int index = 0;
        if (previousChildKey != null) {
            index = getIndexForKey(previousChildKey) + 1;
        }
        mSnapshots.add(index, snapshot);
        notifyChangeEventListeners(ChangeEventListener.EventType.ADDED, index);
    }

    @Override
    public void onChildChanged(DataSnapshot snapshot, String previousChildKey) {
        int index = getIndexForKey(snapshot.getKey());
        mSnapshots.set(index, snapshot);
        notifyChangeEventListeners(ChangeEventListener.EventType.CHANGED, index);
    }

    @Override
    public void onChildRemoved(DataSnapshot snapshot) {
        int index = getIndexForKey(snapshot.getKey());
        mSnapshots.remove(index);
        notifyChangeEventListeners(ChangeEventListener.EventType.REMOVED, index);
    }

    @Override
    public void onChildMoved(DataSnapshot snapshot, String previousChildKey) {
        int oldIndex = getIndexForKey(snapshot.getKey());
        mSnapshots.remove(oldIndex);
        int newIndex = previousChildKey == null ? 0 : (getIndexForKey(previousChildKey) + 1);
        mSnapshots.add(newIndex, snapshot);
        notifyChangeEventListeners(ChangeEventListener.EventType.MOVED, newIndex, oldIndex);
    }

    @Override
    public void onDataChange(DataSnapshot dataSnapshot) {
        notifyListenersOnDataChanged();
    }

    @Override
    public void onCancelled(DatabaseError error) {
        notifyListenersOnCancelled(error);
    }

    private int getIndexForKey(String key) {
        int index = 0;
        for (DataSnapshot snapshot : mSnapshots) {
            if (snapshot.getKey().equals(key)) {
                return index;
            } else {
                index++;
            }
        }
        throw new IllegalArgumentException("Key not found");
    }

    @Override
    public int size() {
        return mSnapshots.size();
    }

    @Override
    public boolean isEmpty() {
        return mSnapshots.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return mSnapshots.contains(o);
    }

    @Override
    public Object[] toArray() {
        return mSnapshots.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return mSnapshots.toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return mSnapshots.containsAll(c);
    }

    @Override
    public DataSnapshot get(int index) {
        return mSnapshots.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return mSnapshots.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return mSnapshots.lastIndexOf(o);
    }

    /**
     * {@inheritDoc}
     *
     * @return an immutable iterator
     */
    @Override
    public Iterator<DataSnapshot> iterator() {
        return new ImmutableIterator(mSnapshots.iterator());
    }

    /**
     * {@inheritDoc}
     *
     * @return an immutable list iterator
     */
    @Override
    public ListIterator<DataSnapshot> listIterator() {
        return new ImmutableListIterator(mSnapshots.listIterator());
    }

    /**
     * {@inheritDoc}
     *
     * @return an immutable list iterator
     */
    @Override
    public ListIterator<DataSnapshot> listIterator(int index) {
        return new ImmutableListIterator(mSnapshots.listIterator(index));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FirebaseArray snapshots = (FirebaseArray) o;

        return mListeners.equals(snapshots.mListeners)
                && mQuery.equals(snapshots.mQuery)
                && mSnapshots.equals(snapshots.mSnapshots);
    }

    @Override
    public int hashCode() {
        int result = mListeners.hashCode();
        result = 31 * result + mQuery.hashCode();
        result = 31 * result + mSnapshots.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "FirebaseArray{" +
                "mIsListening=" + isListening() +
                ", mQuery=" + mQuery +
                ", mSnapshots=" + mSnapshots +
                '}';
    }

    protected static class ImmutableIterator implements Iterator<DataSnapshot> {
        private Iterator<DataSnapshot> mIterator;

        public ImmutableIterator(Iterator<DataSnapshot> iterator) {
            mIterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return mIterator.hasNext();
        }

        @Override
        public DataSnapshot next() {
            return mIterator.next();
        }
    }

    protected static class ImmutableListIterator implements ListIterator<DataSnapshot> {
        private ListIterator<DataSnapshot> mListIterator;

        public ImmutableListIterator(ListIterator<DataSnapshot> listIterator) {
            mListIterator = listIterator;
        }

        @Override
        public boolean hasNext() {
            return mListIterator.hasNext();
        }

        @Override
        public DataSnapshot next() {
            return mListIterator.next();
        }

        @Override
        public boolean hasPrevious() {
            return mListIterator.hasPrevious();
        }

        @Override
        public DataSnapshot previous() {
            return mListIterator.previous();
        }

        @Override
        public int nextIndex() {
            return mListIterator.nextIndex();
        }

        @Override
        public int previousIndex() {
            return mListIterator.previousIndex();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(DataSnapshot snapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(DataSnapshot snapshot) {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public boolean add(DataSnapshot snapshot) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     * @deprecated Unsupported operation.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public boolean addAll(Collection<? extends DataSnapshot> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     * @deprecated Unsupported operation.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public boolean addAll(int index, Collection<? extends DataSnapshot> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public DataSnapshot set(int index, DataSnapshot element) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void add(int index, DataSnapshot element) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public DataSnapshot remove(int index) {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception and leave the collection unmodified.
     *
     * @throws UnsupportedOperationException always
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public List<DataSnapshot> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }
}
