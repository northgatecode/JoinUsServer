package com.northgatecode.joinus.controllers;

import com.northgatecode.joinus.auth.Authenticated;
import com.northgatecode.joinus.auth.UserPrincipal;
import com.northgatecode.joinus.dto.user.*;
import com.northgatecode.joinus.mongodb.City;
import com.northgatecode.joinus.mongodb.Gender;
import com.northgatecode.joinus.mongodb.Image;
import com.northgatecode.joinus.mongodb.User;
import com.northgatecode.joinus.services.UserService;
import com.northgatecode.joinus.utils.MorphiaHelper;
import com.northgatecode.joinus.utils.Utils;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.bson.types.ObjectId;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.mongodb.morphia.Datastore;

import javax.imageio.ImageIO;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Created by qianliang on 28/3/2016.
 */
@Path("myProfile")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MyProfileController {
    private Logger logger = Logger.getLogger(this.getClass().getName());

    @GET
    @Authenticated
    public Response getMyProfile(@Context SecurityContext securityContext) {
        UserPrincipal userPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
        ObjectId userId = userPrincipal.getId();
        User user = UserService.getById(userId);
        return Response.ok(new UserProfile(user)).build();
    }

    @POST
    @Path("password")
    @Authenticated
    public Response updatePassword(@Context SecurityContext securityContext, UserPassword userPassword) {
        UserPrincipal userPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
        ObjectId userId = userPrincipal.getId();

        Datastore datastore = MorphiaHelper.getDatastore();

        User user = datastore.find(User.class).field("id").equal(userId).get();

        if (user.getPassword() != null && !UserService.verifyPassword(user, userPassword.getOldPassword())) {
            throw new BadRequestException("原密码错误");
        }
        // validate password
        if (userPassword.getNewPassword().length() < 6) {
            throw new BadRequestException("无效的新密码");
        }

        String salt = RandomStringUtils.randomAlphanumeric(4);
        String hashedPassword = DigestUtils.md5Hex(userPassword.getNewPassword() + salt);

        user.setPassword(hashedPassword);
        user.setSalt(salt);
        user.setLastUpdateDate(new Date());

        datastore.save(user);


        return Response.ok().build();
    }

    @POST
    @Path("email")
    @Authenticated
    public Response updateEmail(@Context SecurityContext securityContext, UserEmail userEmail) {
        UserPrincipal userPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
        ObjectId userId = userPrincipal.getId();

        Datastore datastore = MorphiaHelper.getDatastore();

        User user = datastore.find(User.class).field("id").equal(userId).get();
        user.setEmail(userEmail.getEmail());
        user.setLastUpdateDate(new Date());

        datastore.save(user);

        return Response.ok(new UserProfile(user)).build();
    }

    @POST
    @Path("name")
    @Authenticated
    public Response updateName(@Context SecurityContext securityContext, UserName userName) {
        UserPrincipal userPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
        ObjectId userId = userPrincipal.getId();

        Datastore datastore = MorphiaHelper.getDatastore();

        User user = datastore.find(User.class).field("id").equal(userId).get();
        user.setName(userName.getName());
        user.setLastUpdateDate(new Date());

        datastore.save(user);

        return Response.ok(new UserProfile(user)).build();
    }

    @POST
    @Path("photo")
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePhoto(@Context SecurityContext securityContext, @FormDataParam("file") InputStream fileInputStream,
                                @FormDataParam("file") FormDataContentDisposition fileMetaData) throws IOException {

        UserPrincipal userPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
        ObjectId userId = userPrincipal.getId();

        Datastore datastore = MorphiaHelper.getDatastore();
        User user = datastore.find(User.class).field("id").equal(userId).get();

        if (!FilenameUtils.getExtension(fileMetaData.getFileName()).toLowerCase().equals("jpg")) {
            throw new BadRequestException("JPG file only");
        }

        String name = UUID.randomUUID().toString();
        String fileName =  name + ".jpg";
        String fullPath = FilenameUtils.concat(Utils.getUploadFolder(), fileName);

        OutputStream out = new FileOutputStream(fullPath);
        int read;
        byte[] bytes = new byte[1024];
        while ((read = fileInputStream.read(bytes)) != -1) {
            out.write(bytes, 0, read);
        }
        out.flush();
        out.close();

        File imageFile = new File(fullPath);
        BufferedImage originalImage = ImageIO.read(imageFile);

        Image image = new Image();
        image.setName(name);
        image.setExtension(".jpg");
        image.setSize(imageFile.length());
        image.setWidth(originalImage.getWidth());
        image.setHeight(originalImage.getHeight());
        image.setUploadedBy(user);
        image.setUploadDate(new Date());

        float shorterSide = image.getHeight() > image.getWidth() ? image.getWidth() : image.getHeight();
        List<String> dimensions = new ArrayList<>();
        Thumbnails.of(imageFile).scale(320 / shorterSide).toFile(FilenameUtils.concat(Utils.getResizedFolder(),
                image.getName() + "_320" + image.getExtension()));
        dimensions.add("_320");
        Thumbnails.of(imageFile).scale(160 / shorterSide).toFile(FilenameUtils.concat(Utils.getResizedFolder(),
                image.getName() + "_160" + image.getExtension()));
        dimensions.add("_160");
        Thumbnails.of(imageFile).scale(80 / shorterSide).toFile(FilenameUtils.concat(Utils.getResizedFolder(),
                image.getName() + "_80" + image.getExtension()));
        dimensions.add("_80");
        image.setDimensions(dimensions);

        datastore.save(image);


        user.setPhoto(image);
        user.setLastUpdateDate(new Date());

        datastore.save(user);

        return Response.ok(new UserProfile(user)).build();
    }

    @POST
    @Path("gender")
    @Authenticated
    public Response updatePhoto(@Context SecurityContext securityContext, UserGender userGender) {
        UserPrincipal userPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
        ObjectId userId = userPrincipal.getId();

        Datastore datastore = MorphiaHelper.getDatastore();

        User user = datastore.find(User.class).field("id").equal(userId).get();
        Gender gender = datastore.find(Gender.class).field("id").equal(userGender.getGenderId()).get();
        if (gender == null) {
            throw new BadRequestException("无效的性别代码");
        }
        user.setGender(gender);
        user.setLastUpdateDate(new Date());

        datastore.save(user);

        return Response.ok(new UserProfile(user)).build();
    }

    @POST
    @Path("city")
    @Authenticated
    public Response updatePhoto(@Context SecurityContext securityContext, UserCity userCity) {
        UserPrincipal userPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
        ObjectId userId = userPrincipal.getId();

        Datastore datastore = MorphiaHelper.getDatastore();

        User user = datastore.find(User.class).field("id").equal(userId).get();
        City city = datastore.find(City.class).field("id").equal(userCity.getCityId()).get();
        if (city == null) {
            throw new BadRequestException("无效的城市代码");
        }
        user.setCity(city);
        user.setLastUpdateDate(new Date());

        datastore.save(user);

        return Response.ok(new UserProfile(user)).build();
    }
}
