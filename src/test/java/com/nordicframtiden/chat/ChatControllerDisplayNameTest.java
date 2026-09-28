package com.nordicframtiden.chat;

import com.nordicframtiden.admin.model.AdminProfile;
import com.nordicframtiden.admin.model.AdminProfileRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.model.UserProfile;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat must display full names, never raw usernames: pure-admin accounts keep
 * their name in AdminProfile (no UserProfile), so the participant mapper has
 * to consult both profile tables before degrading to the username.
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerDisplayNameTest {

    @Mock ChatService service;
    @Mock ChatRoomMemberRepository members;
    @Mock ChatMessageRepository messages;
    @Mock ChatReactionRepository reactions;
    @Mock ChatAttachmentRepository attachments;
    @Mock ChatAttachmentDeliveryRepository deliveries;
    @Mock ChatAttachmentPurgeService purgeService;
    @Mock AppUserRepository users;
    @Mock UserProfileRepository profiles;
    @Mock AdminProfileRepository adminProfiles;
    @Mock ChatPresence presence;
    @Mock com.nordicframtiden.security.service.UserService userService;

    @InjectMocks ChatController controller;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
            .build();
    }

    /** A pure admin: name lives in AdminProfile, no UserProfile. */
    private AppUser adminUser() {
        AppUser user = new AppUser();
        user.setId(2L);
        user.setUsername("alaa.admin");
        user.setEnabled(true);
        user.setRoles(new java.util.HashSet<>(Set.of(Role.ADMIN)));
        return user;
    }

    private AdminProfile adminProfile(String fullName) {
        AdminProfile profile = new AdminProfile();
        profile.setFullName(fullName);
        return profile;
    }

    private UserProfile userProfile(String fullName) {
        UserProfile profile = new UserProfile();
        profile.setFullName(fullName);
        return profile;
    }

    private ChatRoomMember member(ChatRoom room, AppUser user) {
        ChatRoomMember member = new ChatRoomMember();
        member.setRoom(room);
        member.setUser(user);
        return member;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String username) {
        return request -> {
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(username, "n/a"));
            return request;
        };
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /** The viewer: a dual-role admin whose name lives in the user profile. */
    private AppUser viewer() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("viewer");
        user.setEnabled(true);
        user.setRoles(new java.util.HashSet<>(Set.of(Role.USER, Role.ADMIN)));
        return user;
    }

    @Test
    void participants_show_full_name_from_admin_profile_for_pure_admins() throws Exception {
        AppUser viewer = viewer();
        AppUser pureAdmin = adminUser(); // id 1 in adminUser(), only AdminProfile
        when(service.current(any())).thenReturn(viewer);
        when(users.findAll()).thenReturn(List.of(viewer, pureAdmin));
        // The viewer itself is excluded from the list, so only id 2 is resolved.
        when(profiles.findByUserId(2L)).thenReturn(Optional.empty());
        when(adminProfiles.findByUserId(2L)).thenReturn(Optional.of(adminProfile("Alaa Alaleiwi")));
        when(presence.isOnline(anyString())).thenReturn(false);
        lenient().when(userService.photoIdOf(2L)).thenReturn(null);

        mvc.perform(get("/api/chat/participants").with(asUser("viewer")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].displayName").value("Alaa Alaleiwi"))
            .andExpect(jsonPath("$[0].username").value("alaa.admin"))
            .andExpect(jsonPath("$[0].photoId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void participants_expose_photo_id_for_chat_avatars() throws Exception {
        AppUser viewer = viewer();
        AppUser pharmacist = adminUser();
        when(service.current(any())).thenReturn(viewer);
        when(users.findAll()).thenReturn(List.of(viewer, pharmacist));
        when(profiles.findByUserId(2L)).thenReturn(Optional.empty());
        when(adminProfiles.findByUserId(2L)).thenReturn(Optional.of(adminProfile("Alaa Alaleiwi")));
        when(presence.isOnline(anyString())).thenReturn(false);
        when(userService.photoIdOf(2L)).thenReturn(55L);

        mvc.perform(get("/api/chat/participants").with(asUser("viewer")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].photoId").value(55));
    }

    @Test
    void participants_still_prefer_user_profile_when_it_exists() throws Exception {
        AppUser viewer = viewer();
        AppUser dualRole = new AppUser();
        dualRole.setId(2L);
        dualRole.setUsername("dual");
        dualRole.setEnabled(true);
        dualRole.setRoles(new java.util.HashSet<>(Set.of(Role.USER, Role.ADMIN)));

        when(service.current(any())).thenReturn(viewer);
        when(users.findAll()).thenReturn(List.of(viewer, dualRole));
        // The viewer itself is excluded from the list.
        when(profiles.findByUserId(2L)).thenReturn(Optional.of(userProfile("Dual User-Profile Name")));
        // AdminProfile must not override the user profile; the lookup is
        // short-circuited when the user profile exists, hence lenient.
        lenient().when(adminProfiles.findByUserId(2L)).thenReturn(Optional.of(adminProfile("Dual Admin-Profile Name")));
        when(presence.isOnline(anyString())).thenReturn(false);

        mvc.perform(get("/api/chat/participants").with(asUser("viewer")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].displayName").value("Dual User-Profile Name"));
    }

    @Test
    void participants_fall_back_to_username_only_when_no_profile_exists() throws Exception {
        AppUser viewer = viewer();
        AppUser profileless = adminUser();
        when(service.current(any())).thenReturn(viewer);
        when(users.findAll()).thenReturn(List.of(viewer, profileless));
        when(profiles.findByUserId(2L)).thenReturn(Optional.empty());
        when(adminProfiles.findByUserId(2L)).thenReturn(Optional.empty());
        when(presence.isOnline(anyString())).thenReturn(false);

        mvc.perform(get("/api/chat/participants").with(asUser("viewer")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].displayName").value("alaa.admin"));
    }

    @Test
    void direct_room_title_uses_full_name_not_username() throws Exception {
        AppUser viewer = viewer();
        AppUser pureAdmin = adminUser();

        ChatRoom direct = new ChatRoom();
        direct.setId(9L);
        direct.setType(ChatRoom.Type.DIRECT);
        direct.setCreatedBy(pureAdmin);

        when(service.current(any())).thenReturn(viewer);
        when(service.visibleRooms(any())).thenReturn(List.of(direct));
        when(members.findByRoomId(9L)).thenReturn(List.of(member(direct, viewer), member(direct, pureAdmin)));
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(userProfile("Viewer Name")));
        when(profiles.findByUserId(2L)).thenReturn(Optional.empty());
        when(adminProfiles.findByUserId(2L)).thenReturn(Optional.of(adminProfile("Alaa Alaleiwi")));
        lenient().when(presence.isOnline(anyString())).thenReturn(false);
        when(service.unreadCount(9L, 1L)).thenReturn(0L);

        mvc.perform(get("/api/chat/rooms").with(asUser("viewer")).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Alaa Alaleiwi"));
    }
}
