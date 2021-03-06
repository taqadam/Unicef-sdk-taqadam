package com.recoded.taqadam.models.db;

/**
 * Created by wisam on Dec 25 17.
 */

public class PostDbHandler {
    /*
    public static final String
            TITLE = "title",
            BODY = "body",
            TRUNCATED_BODY = "truncated_body",
            TIMESTAMP = "ts",
            USER_ID = "uid",
            AUTHOR = "author",
            AUTHOR_IMG = "u_img",
            COMMENTS = "comments";

    private static PostDbHandler handler;
    private HashMap<String, Post> postsList;
    private Task<List<Post>> latestPostsTask;

    private OnPostsChangedListener postsListener;

    private String mUid;
    private DatabaseReference mThreadsDbRef, mDataDbRef;
    private ChildEventListener mCommentsListener, mThreadsListener;

    public static PostDbHandler getInstance() {
        if (handler == null) {
            handler = new PostDbHandler();
        }
        return handler;
    }

    private PostDbHandler() {
        mUid = UserAuthHandler.getInstance().getUid();
        this.mThreadsDbRef = FirebaseDatabase.getInstance().getReference()
                .child("Posts")
                .child("threads");
        this.mDataDbRef = FirebaseDatabase.getInstance().getReference()
                .child("Posts")
                .child("data");
        this.postsList = new HashMap<>();

        setupLatestThreadsListener();

        //Threads will be categorized by year-month, i.e. 2017-12
        //Read the latest posts (current month)
        mThreadsDbRef.child(getCurrentTimeCycle()).addChildEventListener(mThreadsListener);
        final TaskCompletionSource<List<Post>> fetcher = new TaskCompletionSource<>();
        mThreadsDbRef.child(getCurrentTimeCycle()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //All threads will be added onChildAdded then this will be called so we just set the result

                //If the results are less than 5, read last months as well
                if (dataSnapshot.getChildrenCount() <= 5) {
                    String timeCycle = getPreviousTimeCycle(getCurrentTimeCycle());
                    getPosts(timeCycle).addOnCompleteListener(new OnCompleteListener<HashMap<String, Post>>() {
                        @Override
                        public void onComplete(@NonNull Task<HashMap<String, Post>> task) {
                            if (task.isSuccessful()) {
                                fetcher.setResult(getRecentPosts());
                            } else {
                                fetcher.setException(task.getException());
                            }
                        }
                    });
                } else {
                    fetcher.setResult(getRecentPosts());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                fetcher.setException(databaseError.toException());
            }
        });

        latestPostsTask = fetcher.getTask();
    }

    private void setupLatestThreadsListener() {
        this.mThreadsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Post post = new Post(dataSnapshot.getKey())
                        .fromMap((HashMap<String, Object>) dataSnapshot.getValue());
                postsList.put(post.getId(), post);

                if (postsListener != null) {
                    postsListener.onPostsChanged(getRecentPosts());
                }
                //We need to monitor the list as well or call a callback in here
                //Add all existing children then Single Value Event Listener will be called and we will set the results
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                postsList.remove(dataSnapshot.getKey());
                if (postsListener != null) {
                    postsListener.onPostsChanged(getRecentPosts());
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
    }

    public List<Post> getRecentPosts() {
        List<Post> res = new ArrayList<>();
        for (String key : postsList.keySet()) {
            res.add(postsList.get(key));
        }
        return res;
    }

    public Task<List<Post>> getRecentPostsTask() {
        return latestPostsTask;
    }

    //This will be used to read more older posts only
    @NonNull
    public Task<HashMap<String, Post>> getPosts(String timeCycle) {
        final TaskCompletionSource<HashMap<String, Post>> fetcher = new TaskCompletionSource<>();
        if (mThreadsDbRef == null) {
            fetcher.setException(new NullPointerException());
            return fetcher.getTask();
        }
        if (timeCycle.equals(getCurrentTimeCycle())) {
            fetcher.setResult(null);
            return fetcher.getTask();
        }

        mThreadsDbRef.child(timeCycle).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                HashMap<String, HashMap<String, Object>> posts
                        = (HashMap<String, HashMap<String, Object>>) dataSnapshot.getValue();

                //we will update current posts and return the new posts
                HashMap<String, Post> returnList = new HashMap<>();
                if (posts != null) {
                    for (String key : posts.keySet()) {
                        Post post = new Post(key).fromMap(posts.get(key));
                        postsList.put(key, post);
                        returnList.put(key, post);
                    }
                }
                fetcher.setResult(returnList);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                fetcher.setException(databaseError.toException());
            }
        });
        return fetcher.getTask();
    }

    //This will be used to read the body of single post
    @NonNull
    public Task<Post> getPost(final String postId) {
        final TaskCompletionSource<Post> fetcher = new TaskCompletionSource<>();
        mDataDbRef.child(postId).child(BODY).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                postsList.get(postId).setBody((String) dataSnapshot.getValue());
                getComments(postId).addOnSuccessListener(new OnSuccessListener<List<Comment>>() {
                    @Override
                    public void onSuccess(List<Comment> comments) {
                        postsList.get(postId).setComments(comments);
                        fetcher.setResult(postsList.get(postId));
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        fetcher.setException(e);
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                fetcher.setException(databaseError.toException());
            }
        });

        return fetcher.getTask();
    }

    //This will be used to read comments on a single post
    @NonNull
    public Task<List<Comment>> getComments(final String postId) {
        final TaskCompletionSource<List<Comment>> fetcher = new TaskCompletionSource<>();
        mDataDbRef.child(postId).child(COMMENTS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                HashMap<String, HashMap<String, Object>> comments
                        = (HashMap<String, HashMap<String, Object>>) dataSnapshot.getValue();
                List<Comment> list = new ArrayList<>();
                if (comments != null) {
                    for (String id : comments.keySet()) {
                        Comment comment = new Comment(id).fromMap(comments.get(id));
                        comment.setPostId(postId);
                        list.add(comment);
                    }
                }
                postsList.get(postId).setComments(list);
                fetcher.setResult(list);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                fetcher.setException(databaseError.toException());
            }
        });

        return fetcher.getTask();
    }

    public void deletePost(Post post) {
        if (post.getId() != null && post.getUid().equals(mUid)) {
            mThreadsDbRef.child(getTimeCycleFromTs(post.getPostTime())).child(post.getId()).setValue(null);
            mDataDbRef.child(post.getId())
                    .setValue(null);

            postsList.remove(post.getId());
            if (postsListener != null) postsListener.onPostsChanged(getRecentPosts());
        }
    }

    public void deleteComment(Comment comment) {
        if (comment.getPostId() != null && comment.getId() != null) {
            mDataDbRef.child(comment.getPostId())
                    .child(COMMENTS)
                    .child(comment.getId())
                    .setValue(null);
        }
    }

    public void updatePost(Post post) {
        if (post.getId() != null && post.getUid().equals(mUid)) {
            mThreadsDbRef.child(getTimeCycleFromTs(post.getPostTime())).child(post.getId()).setValue(post.toMap());
            mDataDbRef.child(post.getId())
                    .child(BODY)
                    .setValue(post.getBody());

            postsList.put(post.getId(), post);
            if (postsListener != null) postsListener.onPostsChanged(getRecentPosts());
        }
    }

    public void updateComment(Comment comment) {
        if (comment.getPostId() != null && comment.getId() != null && comment.getUid().equals(mUid)) {
            mDataDbRef.child(comment.getPostId())
                    .child(COMMENTS)
                    .child(comment.getId())
                    .setValue(comment.toMap());
        }
    }

    public Post writePost(Post post) {
        if (post.getId() == null) {
            post.setId(mThreadsDbRef.child(getCurrentTimeCycle()).push().getKey());
            post.setAuthor(UserAuthHandler.getInstance().getCurrentUser().getDisplayName());
            post.setAuthorImg(UserAuthHandler.getInstance().getCurrentUser().getPicturePath());
            post.setUid(mUid);
            post.setNoOfComments(0);
            post.setPostTime(new Date().getTime());
            mThreadsDbRef.child(getCurrentTimeCycle()).child(post.getId()).setValue(post.toMap());
            mDataDbRef.child(post.getId())
                    .child(BODY)
                    .setValue(post.getBody());
        }

        return post;
    }

    public Comment writeComment(final Comment comment) {
        if (comment.getPostId() != null && comment.getId() == null) {
            comment.setId(mDataDbRef.child(comment.getPostId()).child(COMMENTS).push().getKey());
            comment.setAuthor(UserAuthHandler.getInstance().getCurrentUser().getDisplayName());
            comment.setAuthorImage(UserAuthHandler.getInstance().getCurrentUser().getPicturePath());
            comment.setUid(mUid);
            comment.setCommentTime(new Date().getTime());
            mDataDbRef.child(comment.getPostId())
                    .child(COMMENTS)
                    .child(comment.getId())
                    .setValue(comment.toMap());

            long postTs = postsList.get(comment.getPostId()).getPostTime();
            mThreadsDbRef.child(getTimeCycleFromTs(postTs))
                    .child(comment.getPostId()).child(COMMENTS)
                    .runTransaction(new Transaction.Handler() {
                        @Override
                        public Transaction.Result doTransaction(MutableData mutableData) {
                            if (mutableData.getValue() == null)
                                return Transaction.success(mutableData);
                            int comments = ((Long) mutableData.getValue()).intValue();
                            mutableData.setValue(++comments);
                            return Transaction.success(mutableData);
                        }

                        @Override
                        public void onComplete(DatabaseError databaseError, boolean success, DataSnapshot dataSnapshot) {
                            if (success) {
                                if (dataSnapshot.getValue() != null) {
                                    int comments = ((Long) dataSnapshot.getValue()).intValue();
                                    postsList.get(comment.getPostId()).setNoOfComments(comments);
                                    if (postsListener != null)
                                        postsListener.onPostsChanged(getRecentPosts());
                                }
                            }
                        }
                    });
        }
        postsList.get(comment.getPostId()).getComments().add(comment);
        return comment;
    }

    public static String getCurrentTimeCycle() {
        Calendar cal = Calendar.getInstance();
        String dateFormat = "yyyy-MM";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
        return sdf.format(cal.getTime());
    }

    public static String getTimeCycleFromTs(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(ts));
        String dateFormat = "yyyy-MM";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
        return sdf.format(cal.getTime());
    }

    public static String getPreviousTimeCycle(String refTimeCycle) {
        Calendar cal = Calendar.getInstance();
        String dateFormat = "yyyy-MM";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
        try {
            cal.setTime(sdf.parse(refTimeCycle));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        cal.roll(Calendar.MONTH, false);

        return sdf.format(cal.getTime());
    }

    public void release() {
        this.postsList.clear();
        this.mUid = null;
        this.mThreadsDbRef.child(getCurrentTimeCycle()).removeEventListener(mThreadsListener);
        mThreadsListener = null;
        mThreadsDbRef = null;
        handler = null;
    }

    public void setPostsListener(OnPostsChangedListener postsListener) {
        this.postsListener = postsListener;
    }

    public interface OnPostsChangedListener {
        void onPostsChanged(List<Post> posts);
    }
    */
}
