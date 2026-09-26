package com.interntrack;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GetUsersResult;
import com.google.firebase.auth.UidIdentifier;
import java.util.List;
import java.util.ArrayList;

public class TestAuthBatch {
    public static void test() {
        List<UidIdentifier> list = new ArrayList<>();
        list.add(new UidIdentifier("test"));
    }
}
