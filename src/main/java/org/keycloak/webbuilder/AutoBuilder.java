package org.keycloak.webbuilder;

import org.keycloak.webbuilder.builders.AbstractBuilder;
import org.keycloak.webbuilder.builders.BlogBuilder;
import org.keycloak.webbuilder.builders.GuideBuilder;
import org.keycloak.webbuilder.builders.PageBuilder;
import org.keycloak.webbuilder.builders.ResourcesBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class AutoBuilder {

    public static void main(String[] args) throws Exception {
        File rootDir = new File(System.getProperty("user.dir"));
        AutoBuilder builder = new AutoBuilder(rootDir);
        builder.execute();
    }

    private final Context context;
    private final WatchService watcher;
    private final Map<WatchKey, AbstractBuilder[]> builders;

    public AutoBuilder(File rootDir) throws Exception {
        // Do a full build when starting
        new WebBuilder(new File(System.getProperty("user.dir"))).build();

        context = new Context(rootDir);
        watcher = FileSystems.getDefault().newWatchService();
        builders = new HashMap<>();

        AbstractBuilder updateContext = new AbstractBuilder() {
            @Override
            protected void build() throws Exception {
                context.init();
            }

            @Override
            protected String getTitle() {
                return "Update context";
            }
        };
        updateContext.init(context);

        PageBuilder pages = new PageBuilder();
        pages.init(context);

        ResourcesBuilder resources = new ResourcesBuilder();
        resources.init(context);

        GuideBuilder guides = new GuideBuilder();
        guides.init(context);

//        BlogBuilder blogs = new BlogBuilder();
//        blogs.init(context);

        register(context.getPagesDir(), true, pages);
        register(context.getResourcesDir(), true, resources);
        register(context.getGuidesDir(), true, updateContext, guides, pages);
        register(new File(context.getWebSrcDir(), "templates"), true, guides, pages);
//        register(context.getBlogDir(), true, blogs, pages);
    }

    public void execute() throws Exception {
        while(true) {

            WatchKey key = watcher.take();

            for (WatchEvent<?> ignored : key.pollEvents()) {
            }

            for (AbstractBuilder builder : builders.get(key)) {
                try {
                    builder.execute();
                } catch (Exception e) {
                    System.out.println("Failed to run " + builder.getClass());
                    e.printStackTrace();
                }
            }
            key.reset();
        }
    }

    public void register(File dir, boolean recursive, AbstractBuilder... builder) throws IOException {
        System.out.println("Watching: " + dir);
        WatchKey key = dir.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        builders.put(key, builder);

        if (recursive) {
            Arrays.stream(dir.listFiles(d -> d.isDirectory())).forEach(d -> {
                System.out.println("Watching: " + d.getAbsolutePath());
                try {
                    WatchKey key1 = d.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    builders.put(key1, builder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

}
