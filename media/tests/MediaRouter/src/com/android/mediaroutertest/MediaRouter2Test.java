/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mediaroutertest;

import static android.media.MediaRoute2Info.CONNECTION_STATE_CONNECTED;
import static android.media.MediaRoute2Info.CONNECTION_STATE_CONNECTING;
import static android.media.MediaRoute2Info.DEVICE_TYPE_SPEAKER;
import static android.media.MediaRoute2Info.DEVICE_TYPE_TV;
import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_FIXED;
import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE;

import static com.android.mediaroutertest.MediaRouterManagerTest.CATEGORIES_ALL;
import static com.android.mediaroutertest.MediaRouterManagerTest.CATEGORIES_SPECIAL;
import static com.android.mediaroutertest.MediaRouterManagerTest.CATEGORY_SAMPLE;
import static com.android.mediaroutertest.MediaRouterManagerTest.CATEGORY_SPECIAL;
import static com.android.mediaroutertest.MediaRouterManagerTest.ROUTE_ID1;
import static com.android.mediaroutertest.MediaRouterManagerTest.ROUTE_ID3_SESSION_CREATION_FAILED;
import static com.android.mediaroutertest.MediaRouterManagerTest.ROUTE_ID_SPECIAL_CATEGORY;
import static com.android.mediaroutertest.MediaRouterManagerTest.ROUTE_ID_VARIABLE_VOLUME;
import static com.android.mediaroutertest.MediaRouterManagerTest.SYSTEM_PROVIDER_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.SessionCreationCallback;
import android.media.RouteSessionController;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaRouter2Test {
    private static final String TAG = "MediaRouter2Test";
    Context mContext;
    private MediaRouter2 mRouter2;
    private Executor mExecutor;

    private static final int TIMEOUT_MS = 5000;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mRouter2 = MediaRouter2.getInstance(mContext);
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Tests if we get proper routes for application that has special control category.
     */
    @Test
    public void testGetRoutes() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(CATEGORIES_SPECIAL);

        assertEquals(1, routes.size());
        assertNotNull(routes.get(ROUTE_ID_SPECIAL_CATEGORY));
    }

    @Test
    public void testRouteInfoEquality() {
        MediaRoute2Info routeInfo = new MediaRoute2Info.Builder("id", "name")
                .setDescription("description")
                .setClientPackageName("com.android.mediaroutertest")
                .setConnectionState(CONNECTION_STATE_CONNECTING)
                .setIconUri(new Uri.Builder().path("icon").build())
                .setVolume(5)
                .setVolumeMax(20)
                .addSupportedCategory(CATEGORY_SAMPLE)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .setDeviceType(DEVICE_TYPE_SPEAKER)
                .build();

        MediaRoute2Info routeInfoRebuilt = new MediaRoute2Info.Builder(routeInfo).build();
        assertEquals(routeInfo, routeInfoRebuilt);

        Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(routeInfo, 0);
        parcel.setDataPosition(0);
        MediaRoute2Info routeInfoFromParcel = parcel.readParcelable(null);

        assertEquals(routeInfo, routeInfoFromParcel);
    }

    @Test
    public void testRouteInfoInequality() {
        MediaRoute2Info route = new MediaRoute2Info.Builder("id", "name")
                .setDescription("description")
                .setClientPackageName("com.android.mediaroutertest")
                .setConnectionState(CONNECTION_STATE_CONNECTING)
                .setIconUri(new Uri.Builder().path("icon").build())
                .addSupportedCategory(CATEGORY_SAMPLE)
                .setVolume(5)
                .setVolumeMax(20)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .setDeviceType(DEVICE_TYPE_SPEAKER)
                .build();

        MediaRoute2Info routeId = new MediaRoute2Info.Builder(route)
                .setId("another id").build();
        assertNotEquals(route, routeId);

        MediaRoute2Info routeName = new MediaRoute2Info.Builder(route)
                .setName("another name").build();
        assertNotEquals(route, routeName);

        MediaRoute2Info routeDescription = new MediaRoute2Info.Builder(route)
                .setDescription("another description").build();
        assertNotEquals(route, routeDescription);

        MediaRoute2Info routeConnectionState = new MediaRoute2Info.Builder(route)
                .setConnectionState(CONNECTION_STATE_CONNECTED).build();
        assertNotEquals(route, routeConnectionState);

        MediaRoute2Info routeIcon = new MediaRoute2Info.Builder(route)
                .setIconUri(new Uri.Builder().path("new icon").build()).build();
        assertNotEquals(route, routeIcon);

        MediaRoute2Info routeClient = new MediaRoute2Info.Builder(route)
                .setClientPackageName("another.client.package").build();
        assertNotEquals(route, routeClient);

        MediaRoute2Info routeCategory = new MediaRoute2Info.Builder(route)
                .addSupportedCategory(CATEGORY_SPECIAL).build();
        assertNotEquals(route, routeCategory);

        MediaRoute2Info routeVolume = new MediaRoute2Info.Builder(route)
                .setVolume(10).build();
        assertNotEquals(route, routeVolume);

        MediaRoute2Info routeVolumeMax = new MediaRoute2Info.Builder(route)
                .setVolumeMax(30).build();
        assertNotEquals(route, routeVolumeMax);

        MediaRoute2Info routeVolumeHandling = new MediaRoute2Info.Builder(route)
                .setVolumeHandling(PLAYBACK_VOLUME_FIXED).build();
        assertNotEquals(route, routeVolumeHandling);

        MediaRoute2Info routeDeviceType = new MediaRoute2Info.Builder(route)
                .setVolume(DEVICE_TYPE_TV).build();
        assertNotEquals(route, routeDeviceType);
    }

    @Test
    public void testControlVolumeWithRouter() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(CATEGORIES_ALL);

        MediaRoute2Info volRoute = routes.get(ROUTE_ID_VARIABLE_VOLUME);
        assertNotNull(volRoute);

        int originalVolume = volRoute.getVolume();
        int deltaVolume = (originalVolume == volRoute.getVolumeMax() ? -1 : 1);

        awaitOnRouteChanged(
                () -> mRouter2.requestUpdateVolume(volRoute, deltaVolume),
                ROUTE_ID_VARIABLE_VOLUME,
                (route -> route.getVolume() == originalVolume + deltaVolume));

        awaitOnRouteChanged(
                () -> mRouter2.requestSetVolume(volRoute, originalVolume),
                ROUTE_ID_VARIABLE_VOLUME,
                (route -> route.getVolume() == originalVolume));
    }

    @Test
    public void testRequestCreateSessionWithInvalidArguments() {
        MediaRoute2Info route = new MediaRoute2Info.Builder("id", "name").build();
        String controlCategory = "controlCategory";
        Executor executor = mExecutor;
        MediaRouter2.SessionCreationCallback callback = new MediaRouter2.SessionCreationCallback();

        // Tests null route
        assertThrows(NullPointerException.class,
                () -> mRouter2.requestCreateSession(null, controlCategory, executor, callback));

        // Tests null or empty control category
        assertThrows(IllegalArgumentException.class,
                () -> mRouter2.requestCreateSession(route, null, executor, callback));
        assertThrows(IllegalArgumentException.class,
                () -> mRouter2.requestCreateSession(route, "", executor, callback));

        // Tests null executor
        assertThrows(NullPointerException.class,
                () -> mRouter2.requestCreateSession(route, controlCategory, null, callback));

        // Tests null callback
        assertThrows(NullPointerException.class,
                () -> mRouter2.requestCreateSession(route, controlCategory, executor, null));
    }

    @Test
    public void testRequestCreateSessionSuccess() throws Exception {
        final List<String> sampleControlCategory = new ArrayList<>();
        sampleControlCategory.add(CATEGORY_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleControlCategory);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertNotNull(route);

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);

        // Create session with this route
        SessionCreationCallback callback = new SessionCreationCallback() {
            @Override
            public void onSessionCreated(RouteSessionController controller, Bundle controlHints) {
                assertNotNull(controller);
                assertTrue(controller.getSelectedRoutes().contains(ROUTE_ID1));
                assertTrue(TextUtils.equals(CATEGORY_SAMPLE, controller.getCategory()));
                successLatch.countDown();
            }

            @Override
            public void onSessionCreationFailed() {
                failureLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        mRouter2.registerCallback(mExecutor, new MediaRouter2.RouteCallback());

        mRouter2.requestCreateSession(route, CATEGORY_SAMPLE, mExecutor, callback);
        assertTrue(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // onSessionCreationFailed should not be called.
        assertFalse(failureLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRequestCreateSessionFailure() throws Exception {
        final List<String> sampleControlCategory = new ArrayList<>();
        sampleControlCategory.add(CATEGORY_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleControlCategory);
        MediaRoute2Info route = routes.get(ROUTE_ID3_SESSION_CREATION_FAILED);
        assertNotNull(route);

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);

        // Create session with this route
        SessionCreationCallback callback = new SessionCreationCallback() {
            @Override
            public void onSessionCreated(RouteSessionController controller, Bundle controlHints) {
                successLatch.countDown();
            }

            @Override
            public void onSessionCreationFailed() {
                failureLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        mRouter2.registerCallback(mExecutor, new MediaRouter2.RouteCallback());

        mRouter2.requestCreateSession(route, CATEGORY_SAMPLE, mExecutor, callback);
        assertTrue(failureLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // onSessionCreated should not be called.
        assertFalse(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRequestCreateSessionMultipleSessions() throws Exception {
        // TODO: Test creating multiple sessions (Check the ID of each controller)
    }

    // Helper for getting routes easily
    static Map<String, MediaRoute2Info> createRouteMap(List<MediaRoute2Info> routes) {
        Map<String, MediaRoute2Info> routeMap = new HashMap<>();
        for (MediaRoute2Info route : routes) {
            // intentionally not using route.getUniqueId() for convenience.
            routeMap.put(route.getId(), route);
        }
        return routeMap;
    }

    Map<String, MediaRoute2Info> waitAndGetRoutes(List<String> controlCategories)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // A dummy callback is required to send control category info.
        MediaRouter2.RouteCallback
                routeCallback = new MediaRouter2.RouteCallback() {
            @Override
            public void onRoutesAdded(List<MediaRoute2Info> routes) {
                for (int i = 0; i < routes.size(); i++) {
                    //TODO: use isSystem() or similar method when it's ready
                    if (!TextUtils.equals(routes.get(i).getProviderId(), SYSTEM_PROVIDER_ID)) {
                        latch.countDown();
                    }
                }
            }
        };

        mRouter2.setControlCategories(controlCategories);
        mRouter2.registerCallback(mExecutor, routeCallback);
        try {
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return createRouteMap(mRouter2.getRoutes());
        } finally {
            mRouter2.unregisterCallback(routeCallback);
        }
    }

    void awaitOnRouteChanged(Runnable task, String routeId,
            Predicate<MediaRoute2Info> predicate) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        MediaRouter2.RouteCallback
                routeCallback = new MediaRouter2.RouteCallback() {
            @Override
            public void onRoutesChanged(List<MediaRoute2Info> changed) {
                MediaRoute2Info route = createRouteMap(changed).get(routeId);
                if (route != null && predicate.test(route)) {
                    latch.countDown();
                }
            }
        };
        mRouter2.registerCallback(mExecutor, routeCallback);
        try {
            task.run();
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            mRouter2.unregisterCallback(routeCallback);
        }
    }
}
