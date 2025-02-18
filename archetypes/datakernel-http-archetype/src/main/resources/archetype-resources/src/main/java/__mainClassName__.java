package ${groupId};

import io.datakernel.async.Promise;
import io.datakernel.di.annotation.Provides;
import io.datakernel.http.AsyncServlet;
import io.datakernel.launcher.Launcher;
import io.datakernel.launchers.http.HttpServerLauncher;

import java.time.LocalDateTime;

import static io.datakernel.http.HttpResponse.ok200;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

public class ${mainClassName} extends HttpServerLauncher {

    @Provides
    AsyncServlet servlet() {
        return request -> Promise.of(ok200().withPlainText("Hello World at " +
			LocalDateTime.now().format(ISO_LOCAL_DATE_TIME)));
    }

    public static void main(String[] args) throws Exception {
        Launcher launcher = new ${mainClassName}();
        launcher.launch(args);
    }
}

