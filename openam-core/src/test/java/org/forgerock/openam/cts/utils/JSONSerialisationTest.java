/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.openam.cts.utils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.*;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.iplanet.dpro.session.DNOrIPAddressListTokenRestriction;
import com.iplanet.dpro.session.SessionID;
import com.iplanet.dpro.session.TokenRestriction;
import com.iplanet.dpro.session.service.InternalSession;
import org.forgerock.audit.events.EventTopicsMetaData;
import org.forgerock.guice.core.GuiceModules;
import org.forgerock.guice.core.GuiceTestCase;
import org.forgerock.guice.core.InjectorHolder;
import org.forgerock.openam.audit.AMAuditService;
import org.forgerock.openam.audit.AuditEventPublisher;
import org.forgerock.openam.audit.AuditEventPublisherImpl;
import org.forgerock.openam.audit.AuditServiceProvider;
import org.forgerock.openam.audit.configuration.AMAuditServiceConfiguration;
import org.forgerock.openam.audit.configuration.AuditEventHandlerConfiguration;
import org.forgerock.openam.audit.configuration.AuditServiceConfigurationListener;
import org.forgerock.openam.audit.configuration.AuditServiceConfigurationProvider;
import org.forgerock.openam.core.guice.CoreGuiceModule;
import org.forgerock.openam.core.guice.DataLayerGuiceModule;
import org.forgerock.openam.cts.TokenTestUtils;
import org.forgerock.openam.cts.api.tokens.Token;
import org.forgerock.openam.notifications.NotificationBroker;
import org.forgerock.openam.shared.guice.SharedGuiceModule;
import org.forgerock.openam.tokens.TokenType;
import org.forgerock.openam.utils.IOUtils;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@GuiceModules({CoreGuiceModule.class, SharedGuiceModule.class, DataLayerGuiceModule.class,
        JSONSerialisationTest.DummyConfigModule.class})
public class JSONSerialisationTest extends GuiceTestCase {

    private JSONSerialisation serialization;

    @BeforeMethod
    public void setup() throws Exception {
        serialization = InjectorHolder.getInstance(JSONSerialisation.class);
    }

    @Test
    public void shouldSerialiseAString() {
        // Given
        String test = "Badger";
        // When
        String result = serialization.deserialise(serialization.serialise(test), String.class);
        // Then
        assertEquals(test, result);
    }

    @Test
    public void shouldSerialiseAMap() {
        // Given
        Map<String, Object> test = new HashMap<String, Object>();
        test.put("badger", 1234);
        test.put("ferret", 4321);

        // When
        String text = serialization.serialise(test);
        Map<String, Object> result = serialization.deserialise(text, Map.class);
        // Then
        assertEquals(test, result);
    }

    @Test
    public void shouldDeserialiseSerialisedToken() {
        // Given
        Token token = new Token("id", TokenType.OAUTH);

        // When
        Token result = serialization.deserialise(serialization.serialise(token), Token.class);

        // Then
        TokenTestUtils.assertTokenEquals(result, token);
    }

    @Test
    public void shouldSerialiseAndDeserialiseAToken() {
        // Given
        Token token = TokenTestUtils.generateToken();
        // When
        String text = serialization.serialise(token);
        Token result = serialization.deserialise(text, Token.class);
        // Then
        TokenTestUtils.assertTokenEquals(result, token);
    }

    @Test
    public void basicSessionSerializationWorks() throws Exception {
        InternalSession is = new InternalSession();
        String serialised = serialization.serialise(is);
        assertThat(serialised).isNotNull().isEqualTo(getJSON("/json/basic-session.json"));
        assertThat(is).isNotNull();
    }

    @Test
    public void tokenRestrictionDeserialisationWithTypeWorks() throws Exception {
        InternalSession is = serialization.deserialise(getJSON("/json/basic-session-with-restriction.json"),
                InternalSession.class);
        assertThat(is).isNotNull();
        TokenRestriction restriction = is.getRestrictionForToken(new SessionID("AQIC5wM2LY4SfcyTLz6VjQ7nkFeDcEh8K5dXkIE"
                + "NpXlpg28.*AAJTSQACMDIAAlMxAAIwMQACU0sAEzc5ODIzMDM5MzQyNzU2MTg1NDQ.*"));
        assertThat(restriction).isNotNull().isInstanceOf(DNOrIPAddressListTokenRestriction.class);
        assertThat(restriction.toString().equals("Fzy2GsI/O1TsXhvlVuqjqIuTG2k="));
        assertThat(is.getSessionHandle()).isNotNull().isEqualTo("shandle:weasel");
    }

    @DataProvider(name = "complex")
    public Object[][] getComplexJSONs() {
        return new Object[][]{
            {"/json/complex-session-with-restriction-v11.json"},
            {"/json/complex-session-with-restriction-v12.json"}
        };
    }

    @Test(dataProvider = "complex")
    public void internalSessionDeserialisationWorks(String path) throws Exception {
        InternalSession is = serialization.deserialise(getJSON(path), InternalSession.class);
        assertThat(is).isNotNull();
        assertThat(is.getID()).isNotNull();
        assertThat(Collections.list(is.getPropertyNames())).hasSize(23);
        assertThat(is.getSessionHandle()).isNotNull();
    }

    @Test(dataProvider = "complex")
    public void internalSessionDeserialisationDoesNotModifyMapTypes(String path) throws Exception {
        InternalSession is = serialization.deserialise(getJSON(path), InternalSession.class);
        assertThat(is).isNotNull();
        checkMapType(is, "sessionEventURLs");
        checkMapType(is, "restrictedTokensBySid");
        checkMapType(is, "restrictedTokensByRestriction");
    }

    @Test(dataProvider = "complex")
    public void complexInternalSessionSerializationWorks(String path) throws Exception {
        InternalSession is = serialization.deserialise(getJSON(path), InternalSession.class);
        assertThat(is).isNotNull();
        String serialised = serialization.serialise(is);
        assertThat(serialised).isNotNull().isNotEmpty();
        InternalSession is2 = serialization.deserialise(serialised, InternalSession.class);
        assertThat(is2).isNotNull().isNotSameAs(is);
        assertThat(is.getID()).isEqualTo(is2.getID());
    }

    @Test
    public void shouldChangeAttributeName() {
        String name = "badger";
        assertNotEquals(name, JSONSerialisation.jsonAttributeName(name));
    }

    private static String getJSON(String path) throws Exception {
        return IOUtils.getFileContentFromClassPath(JSONSerialisationTest.class, path).replaceAll("\\s", "");
    }

    private static void checkMapType(InternalSession is, String fieldName) throws Exception {
        Field field = is.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object obj = field.get(is);
        assertThat(obj).isInstanceOf(ConcurrentHashMap.class);
    }

    public static class DummyConfigModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(AuditServiceConfigurationProvider.class).to(DummyAuditServiceConfigurationProvider.class);
            bind(AuditServiceProvider.class).to(DummyAuditServiceProvider.class);
            bind(AuditEventPublisher.class).to(AuditEventPublisherImpl.class);
            bind(NotificationBroker.class).toInstance(mock(NotificationBroker.class));
        }
    }

    public static final class DummyAuditServiceConfigurationProvider implements AuditServiceConfigurationProvider {

        @Override
        public void setupComplete() {

        }

        @Override
        public void addConfigurationListener(AuditServiceConfigurationListener listener) {

        }

        @Override
        public void removeConfigurationListener(AuditServiceConfigurationListener listener) {

        }

        @Override
        public AMAuditServiceConfiguration getDefaultConfiguration() {
            return new AMAuditServiceConfiguration(false);
        }

        @Override
        public AMAuditServiceConfiguration getRealmConfiguration(String realm) {
            return new AMAuditServiceConfiguration(false);
        }

        @Override
        public Set<AuditEventHandlerConfiguration> getDefaultEventHandlerConfigurations() {
            return null;
        }

        @Override
        public Set<AuditEventHandlerConfiguration> getRealmEventHandlerConfigurations(String realm) {
            return null;
        }

        @Override
        public EventTopicsMetaData getEventTopicsMetaData() {
            return null;
        }
    }

    public static final class DummyAuditServiceProvider implements AuditServiceProvider {

        @Override
        public AMAuditService getAuditService(String realm) {
            return null;
        }

        @Override
        public AMAuditService getDefaultAuditService() {
            return null;
        }
    }

}
