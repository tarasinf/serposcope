package serposcope.controllers;
import com.serphacker.serposcope.db.google.GoogleDB;
import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.models.base.User;
import com.serphacker.serposcope.models.base.Group.Module;
import com.serphacker.serposcope.models.google.GoogleTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ninja.Context;
import ninja.Result;
import ninja.Results;
import javax.inject.Singleton;
import ninja.params.Param;
import ninja.params.PathParam;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import ninja.params.Params;
import com.serphacker.serposcope.models.base.Group;
import com.serphacker.serposcope.models.google.GoogleSearch;
import com.serphacker.serposcope.scraper.google.GoogleDevice;
import com.serphacker.serposcope.db.google.GoogleDB;
import serposcope.controllers.google.GoogleGroupController;
import serposcope.helpers.Validator;
import com.google.inject.Inject;

@Singleton
public class ApiController {
    @Inject
    GoogleDB googleDB;
    @Inject
    BaseDB baseDB;

    final Object searchLock = new Object();
//    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

    public Result apiAddSearch(
            @Param("user_id") String user_id,
            @Param("group_name") String group_name,

            @Param("target-name") String targetName,
            @Param("target-radio") String targetType,
            @Param("target-pattern") String targetPattern,

            @Params("keyword[]") String[] keywords,
            @Params("tld[]") String tlds[], @Params("datacenter[]") String[] datacenters,
            @Params("device[]") Integer[] devices,
            @Params("local[]") String[] locals, @Params("custom[]") String[] customs
    ) {

        if (user_id == null || group_name == null || keywords == null || tlds == null || datacenters == null
                || devices == null || locals == null || customs == null
                || keywords.length != tlds.length || keywords.length != datacenters.length
                || keywords.length != devices.length || keywords.length != locals.length
                || keywords.length != customs.length) {
            return Results.json().render("{'result': {'ERROR': 'INVALID PARAMS'}}");
        }

        User user = baseDB.user.findById(Integer.parseInt(user_id));
        if (user == null) {
            return Results.json().render("{'result': {'ERROR': 'USER IS NULL'}}");
        }

        Group group = null;
        List<Group> groups = baseDB.group.listForUser(user);
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getName().equals(group_name)) {
                group = groups.get(i);
                break;
            }
        }
        if (group == null) {
            try {
                group = new Group(Group.Module.GOOGLE, group_name);
                baseDB.group.insert(group);
            }catch(Exception ex){
                return Results.json().render("{'result': {'ERROR': 'CANT TO CREATE GROUP'}}");
            }
        }

        if (googleDB.target.list(Arrays.asList(group.getId())).size() == 0) {
            GoogleTarget.PatternType targettype = null;
            try {
                targettype = GoogleTarget.PatternType.valueOf(targetType);
            } catch (Exception ex) {
                return Results.json().render("{'result': {'ERROR': 'INVALID TARGET TYPE'}}");
            }

            // GoogleTarget.PatternType.REGEX -- targettype
            GoogleTarget target = new GoogleTarget(group.getId(), targetName, targettype, targetPattern);
            if (googleDB.target.insert(Arrays.asList(target)) < 1) {
                return Results.json().render("{'result': {'ERROR': 'CANT TO CREATE TARGET'}}");
            }
        }

        List<GoogleSearch> searches = new ArrayList<>();
        for (int i = 0; i < keywords.length; i++) {
            GoogleSearch search = new GoogleSearch();

            if (keywords[i].isEmpty()) {
                return Results.json().render("{'result': {'ERROR': 'KEYWORD EMPTY'}}");
            }
            search.setKeyword(keywords[i]);

            if (!Validator.isGoogleTLD(tlds[i])) {
                return Results.json().render("{'result': {'ERROR': 'INVALID TLD'}}");
            }
            search.setTld(tlds[i]);

            if (!datacenters[i].isEmpty()) {
                if (!Validator.isIPv4(datacenters[i])) {
                    return Results.json().render("{'result': {'ERROR': 'INVALID IP'}}");
                }
                search.setDatacenter(datacenters[i]);
            }

            if (devices[i] != null && devices[i] >= 0 && devices[i] < GoogleDevice.values().length) {
                search.setDevice(GoogleDevice.values()[devices[i]]);
            } else {
                search.setDevice(GoogleDevice.DESKTOP);
            }

            if (!Validator.isEmpty(locals[i])) {
                search.setLocal(locals[i]);
            }

            if (!Validator.isEmpty(customs[i])) {
                search.setCustomParameters(customs[i]);
            }

            searches.add(search);
        }

        synchronized (searchLock) {
            for (GoogleSearch search : searches) {
                int id = googleDB.search.getId(search);
                if (id > 0) { search.setId(id); }
            }
            googleDB.search.insert(searches, group.getId());
        }
        return Results.ok().json().render("{'result': {'OK': ':)'}}");
    }
}