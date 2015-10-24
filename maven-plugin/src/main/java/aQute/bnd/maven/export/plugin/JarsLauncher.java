package aQute.bnd.maven.export.plugin;

import aQute.bnd.build.Container;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.regex.*;

import aQute.bnd.header.*;
import aQute.bnd.osgi.*;
import aQute.launcher.constants.LauncherConstants;
import aQute.launcher.pre.EmbeddedLauncher;
import aQute.lib.utf8properties.UTF8Properties;
import aQute.libg.command.*;
import aQute.libg.cryptography.SHA1;
import aQute.libg.generics.*;

/**
 * Launchers are JARs that launch a framework and install a number of bundles 
 * and then run the framework. A launcher jar must specify a Launcher-Class manifest header. This
 * class is instantiated and cast to a LauncherPlugin. This plug in is then
 * asked to provide a ProjectLauncher. This project launcher is then used by the
 * project to run the code. Launchers must extend this class.
 */
public class JarsLauncher extends Processor {

    private long timeout = 0;
    private final List<String> runPath = new ArrayList<String>();
    private List<String> runbundles = Create.list();
    private final List<String> runvm = new ArrayList<String>();
    private final List<String> runprogramargs = new ArrayList<String>();
    private Map<String, String> runproperties = new HashMap<>();
    private Command java;
    private Parameters runsystempackages;
    private Parameters runsystemcapabilities;
    private final List<String> activators = Create.list();
    private File storageDir;

    private boolean trace;
    private boolean keep;
    private int framework;
    private File cwd;
    private Collection<String> agents = new ArrayList<String>();
    private Map<NotificationListener, Boolean> listeners = new IdentityHashMap<NotificationListener, Boolean>();

    protected Appendable out = System.out;
    protected Appendable err = System.err;
    protected InputStream in = System.in;

    public final static int SERVICES = 10111;
    public final static int NONE = 20123;

    // MUST BE ALIGNED WITH LAUNCHER
    public final static int OK = 0;
    public final static int WARNING = -1;
    public final static int ERROR = -2;
    public final static int TIMEDOUT = -3;
    public final static int UPDATE_NEEDED = -4;
    public final static int CANCELED = -5;
    public final static int DUPLICATE_BUNDLE = -6;
    public final static int RESOLVE_ERROR = -7;
    public final static int ACTIVATOR_ERROR = -8;
    public final static int CUSTOM_LAUNCHER = -128;

    public final static String EMBEDDED_ACTIVATOR = "Embedded-Activator";

    private static final String EMBEDDED_LAUNCHER_FQN = "aQute.launcher.pre.EmbeddedLauncher";
    private static final String EMBEDDED_LAUNCHER = "aQute/launcher/pre/EmbeddedLauncher.class";
    private static final String JPM_LAUNCHER = "aQute/launcher/pre/JpmLauncher.class";
    private static final String JPM_LAUNCHER_FQN = "aQute.launcher.pre.JpmLauncher";

    final private File propertiesFile;
    boolean prepared;

    DatagramSocket listenerComms;

 
    public String getMainTypeName() {
        return "aQute.launcher.Launcher";
    }

    public void update() throws Exception {
        updateFromProject();
        writeProperties();
    }

    public void prepare() throws Exception {
        if (prepared) {
            return;
        }
        prepared = true;
        writeProperties();
    }

    void writeProperties() throws Exception {
        LauncherConstants lc = getConstants(getRunBundles(), false);
        OutputStream out = new FileOutputStream(propertiesFile);
        try {
            lc.getProperties(new UTF8Properties()).store(out, "Launching ...");
        } finally {
            out.close();
        }
    }

    /**
     * @return @throws Exception @throws FileNotFoundException @throws
     * IOException
     */
    private LauncherConstants getConstants(Collection<String> runbundles, boolean exported)
            throws Exception, FileNotFoundException, IOException {
        //project.trace("preparing the aQute launcher plugin");

        LauncherConstants lc = new LauncherConstants();
        //lc.noreferences = Processor.isTrue(project.getProperty(Constants.RUNNOREFERENCES));
        lc.runProperties = getRunProperties();
        lc.storageDir = getStorageDir();
        lc.keep = isKeep();
        lc.runbundles.addAll(runbundles);
        lc.trace = getTrace();
        lc.timeout = getTimeout();
        lc.activators.addAll(getActivators());
        //lc.name = getProject().getName();
        //well, there must be a better way than this:
        lc.systemPackages = "org.osgi.framework;version=\"1.8\","
                + "org.osgi.framework.dto;version=\"1.8\";uses:=\"org.osgi.dto\","
                + "org.osgi.framework.hooks.bundle;version=\"1.1\";uses:=\"org.osgi.framework\","
                + "org.osgi.framework.hooks.resolver;version=\"1.0\";uses:=\"org.osgi.framework.wiring\","
                + "org.osgi.framework.hooks.service;version=\"1.1\";uses:=\"org.osgi.framework\","
                + "org.osgi.framework.hooks.weaving;version=\"1.1\";uses:=\"org.osgi.framework.wiring\","
                + "org.osgi.framework.launch;version=\"1.2\";uses:=\"org.osgi.framework\","
                + "org.osgi.framework.namespace;version=\"1.1\";uses:=\"org.osgi.resource\","
                + "org.osgi.framework.startlevel;version=\"1.0\";uses:=\"org.osgi.framework\","
                + "org.osgi.framework.startlevel.dto;version=\"1.0\";uses:=\"org.osgi.dto\","
                + "org.osgi.framework.wiring;version=\"1.2\";uses:=\"org.osgi.framework,org.osgi.resource\","
                + "org.osgi.framework.wiring.dto;version=\"1.2\";uses:=\"org.osgi.dto,org.osgi.resource.dto\","
                + "org.osgi.resource;version=\"1.0\",org.osgi.resource.dto;version=\"1.0\";uses:=\"org.osgi.dto\","
                + "org.osgi.service.packageadmin;version=\"1.2\";uses:=\"org.osgi.framework\","
                + "org.osgi.service.startlevel;version=\"1.1\";uses:=\"org.osgi.framework\","
                + "org.osgi.service.url;version=\"1.0\","
                + "org.osgi.service.resolver;version=\"1.0\";uses:=\"org.osgi.resource\","
                + "org.osgi.util.tracker;version=\"1.5.1\";uses:=\"org.osgi.framework\",org.osgi.dto;version=\"1.0\"";
        //launch.system.packages=org.osgi.framework;version\="1.8",org.osgi.framework.dto;version\="1.8";uses\:\="org.osgi.dto",org.osgi.framework.hooks.bundle;version\="1.1";uses\:\="org.osgi.framework",org.osgi.framework.hooks.resolver;version\="1.0";uses\:\="org.osgi.framework.wiring",org.osgi.framework.hooks.service;version\="1.1";uses\:\="org.osgi.framework",org.osgi.framework.hooks.weaving;version\="1.1";uses\:\="org.osgi.framework.wiring",org.osgi.framework.launch;version\="1.2";uses\:\="org.osgi.framework",org.osgi.framework.namespace;version\="1.1";uses\:\="org.osgi.resource",org.osgi.framework.startlevel;version\="1.0";uses\:\="org.osgi.framework",org.osgi.framework.startlevel.dto;version\="1.0";uses\:\="org.osgi.dto",org.osgi.framework.wiring;version\="1.2";uses\:\="org.osgi.framework,org.osgi.resource",org.osgi.framework.wiring.dto;version\="1.2";uses\:\="org.osgi.dto,org.osgi.resource.dto",org.osgi.resource;version\="1.0",org.osgi.resource.dto;version\="1.0";uses\:\="org.osgi.dto",org.osgi.service.packageadmin;version\="1.2";uses\:\="org.osgi.framework",org.osgi.service.startlevel;version\="1.1";uses\:\="org.osgi.framework",org.osgi.service.url;version\="1.0",org.osgi.service.resolver;version\="1.0";uses\:\="org.osgi.resource",org.osgi.util.tracker;version\="1.5.1";uses\:\="org.osgi.framework",org.osgi.dto;version\="1.0"


        if (!exported && !getNotificationListeners().isEmpty()) {
            if (listenerComms == null) {
                listenerComms = new DatagramSocket(new InetSocketAddress(InetAddress.getLocalHost(), 0));
                new Thread(new Runnable() {
                    public void run() {
                        DatagramSocket socket = listenerComms;
                        DatagramPacket packet = new DatagramPacket(new byte[65536], 65536);
                        while (!socket.isClosed()) {
                            try {
                                socket.receive(packet);
                                DataInputStream dai = new DataInputStream(new ByteArrayInputStream(packet.getData(),
                                        packet.getOffset(), packet.getLength()));
                                NotificationType type = NotificationType.values()[dai.readInt()];
                                String message = dai.readUTF();
                                for (NotificationListener listener : getNotificationListeners()) {
                                    listener.notify(type, message);
                                }
                            } catch (IOException e) {
                            }
                        }
                    }
                }).start();
            }
            lc.notificationPort = listenerComms.getLocalPort();
        } else {
            lc.notificationPort = -1;
        }

        try {
			// If the workspace contains a newer version of biz.aQute.launcher
            // than the version of bnd(tools) used
            // then this could throw NoSuchMethodError. For now just ignore it.
            Map<String, ? extends Map<String, String>> systemPkgs = getSystemPackages();
            if (systemPkgs != null && !systemPkgs.isEmpty()) {
                lc.systemPackages = Processor.printClauses(systemPkgs);
            }
        } catch (Throwable e) {
        }

        try {
			// If the workspace contains a newer version of biz.aQute.launcher
            // than the version of bnd(tools) used
            // then this could throw NoSuchMethodError. For now just ignore it.
            String systemCaps = getSystemCapabilities();
            if (systemCaps != null) {
                systemCaps = systemCaps.trim();
                if (systemCaps.length() > 0) {
                    lc.systemCapabilities = systemCaps;
                }
            }
        } catch (Throwable e) {
        }
        return lc;

    }

    /**
     * Create a standalone executable. All entries on the runpath are rolled out
     * into the JAR and the runbundles are copied to a directory in the jar. The
     * launcher will see that it starts in embedded mode and will automatically
     * detect that it should load the bundles from inside. This is drive by the
     * launcher.embedded flag. @throws Exception
     */
    public Jar executable() throws Exception {

		// TODO use constants in the future
        //Parameters packageHeader = OSGiHeader.parseHeader(project.getProperty("-package"));
//		boolean useShas = packageHeader.containsKey("jpm");
//		//project.trace("UseshuseShasas %s %s", useShas, packageHeader);
        boolean useShas = false;

        //Jar jar = new Jar(project.getName());
        Jar jar = new Jar("foo");

        Builder b = new Builder();
//		project.addClose(b);

//		if (!project.getIncludeResource().isEmpty()) {
//			b.setIncludeResource(project.getIncludeResource().toString());
//			b.setProperty(Constants.RESOURCEONLY, "true");
//			b.build();
//			if (b.isOk()) {
//				jar.addAll(b.getJar());
//			}
//			project.getInfo(b);
//		}
        List<String> runpath = getRunpath();

        Set<String> runpathShas = new LinkedHashSet<String>();
        Set<String> runbundleShas = new LinkedHashSet<String>();
        List<String> classpath = new ArrayList<String>();

        for (String path : runpath) {
            //project.trace("embedding runpath %s", path);
            File file = new File(path);
            if (file.isFile()) {
                if (useShas) {
                    String sha = SHA1.digest(file).asHex();
                    runpathShas.add(sha + ";name=\"" + file.getName() + "\"");
                } else {
                    String newPath = "jar/" + file.getName();
                    jar.putResource(newPath, new FileResource(file));
                    classpath.add(newPath);
                }
            }
        }

		// Copy the bundles to the JAR
        List<String> runbundles = (List<String>) getRunBundles();
        List<String> actualPaths = new ArrayList<String>();

        for (String path : runbundles) {
            //project.trace("embedding run bundles %s", path);
            File file = new File(path);
            if (!file.isFile()) {
                //project.error("Invalid entry in -runbundles %s", file);
            } else {
                if (useShas) {
                    String sha = SHA1.digest(file).asHex();
                    runbundleShas.add(sha + ";name=\"" + file.getName() + "\"");
                    actualPaths.add("${JPMREPO}/" + sha);
                } else {
                    String newPath = "jar/" + file.getName();
                    jar.putResource(newPath, new FileResource(file));
                    actualPaths.add(newPath);
                }
            }
        }

        LauncherConstants lc = getConstants(actualPaths, true);
        lc.embedded = !useShas;
        lc.storageDir = null; // cannot use local info

        final Properties p = lc.getProperties(new UTF8Properties());

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        p.store(bout, "");
        jar.putResource(LauncherConstants.DEFAULT_LAUNCHER_PROPERTIES, new EmbeddedResource(bout.toByteArray(), 0L));

        Manifest m = new Manifest();
        Attributes main = m.getMainAttributes();

//		for (Entry<Object,Object> e : project.getFlattenedProperties().entrySet()) {
//			String key = (String) e.getKey();
//			if (key.length() > 0 && Character.isUpperCase(key.charAt(0)))
//				main.putValue(key, (String) e.getValue());
//		}
        Instructions instructions = new Instructions();//project.getProperty(Constants.REMOVEHEADERS));
        Collection<Object> result = instructions.select(main.keySet(), false);
        main.keySet().removeAll(result);

        if (useShas) {
            //project.trace("Use JPM launcher");
            m.getMainAttributes().putValue("Main-Class", JPM_LAUNCHER_FQN);
            m.getMainAttributes().putValue("JPM-Classpath", Processor.join(runpathShas));
            m.getMainAttributes().putValue("JPM-Runbundles", Processor.join(runbundleShas));
            URLResource jpmLauncher = new URLResource(this.getClass().getResource("/" + JPM_LAUNCHER));
            jar.putResource(JPM_LAUNCHER, jpmLauncher);
            doStart(jar, JPM_LAUNCHER_FQN);
        } else {
            //project.trace("Use Embedded launcher");
            m.getMainAttributes().putValue("Main-Class", EMBEDDED_LAUNCHER_FQN);
            m.getMainAttributes().putValue(EmbeddedLauncher.EMBEDDED_RUNPATH, Processor.join(classpath));
            URLResource embeddedLauncher = new URLResource(this.getClass().getResource("/" + EMBEDDED_LAUNCHER));
            jar.putResource(EMBEDDED_LAUNCHER, embeddedLauncher);
            doStart(jar, EMBEDDED_LAUNCHER_FQN);
        }
//		if (project.getProperty(Constants.DIGESTS) != null)
//			jar.setDigestAlgorithms(project.getProperty(Constants.DIGESTS).trim().split("\\s*,\\s*"));
//		else
        jar.setDigestAlgorithms(new String[]{
            "SHA-1", "MD-5"
        });
        jar.setManifest(m);
        return jar;
    }

    /*
     * Useful for when exported as folder or unzipped
     */
    void doStart(Jar jar, String fqn) throws UnsupportedEncodingException {
        String nix = "#!/bin/sh\njava -cp . " + fqn + "\n";
        String pc = "java -cp . " + fqn + "\r\n";
        jar.putResource("start", new EmbeddedResource(nix, 0));
        jar.putResource("start.bat", new EmbeddedResource(pc, 0));
    }

    public JarsLauncher() throws Exception {

        updateFromProject();
        propertiesFile = File.createTempFile("launch", ".properties");
//		//project.trace(
//				MessageFormat.format("launcher plugin using temp launch file {0}", propertiesFile.getAbsolutePath()));
        addRunVM("-D" + LauncherConstants.LAUNCHER_PROPERTIES + "=\"" + propertiesFile.getAbsolutePath() + "\"");

//		if (project.getRunProperties().get("noframework") != null) {
//			setRunFramework(NONE);
//			project.warning(
//					"The noframework property in -runproperties is replaced by a project setting: '-runframework: none'");
//		}
        addDefault(Constants.DEFAULT_LAUNCHER_BSN);
    }

    /**
     * Collect all the aspect from the project and set the local fields from
     * them. Should be called @throws Exception
     */
    protected void updateFromProject() throws Exception {
//		setCwd(project.getBase());
//
//		// pkr: could not use this because this is killing the runtests.
//		// project.refresh();
//		runbundles.clear();
//		Collection<Container> run = project.getRunbundles();
//
//		for (Container container : run) {
//			File file = container.getFile();
//			if (file != null && (file.isFile() || file.isDirectory())) {
//				runbundles.add(file.getAbsolutePath());
//			} else {
//				error("Bundle file \"%s\" does not exist, given error is %s", file, container.getError());
//			}
//		}
//
//		if (project.getRunBuilds()) {
//			File[] builds = project.build();
//			if (builds != null)
//				for (File file : builds)
//					runbundles.add(file.getAbsolutePath());
//		}
//
//		Collection<Container> runpath = project.getRunpath();
//		runsystempackages = new Parameters(project.mergeProperties(Constants.RUNSYSTEMPACKAGES));
//		runsystemcapabilities = new Parameters(project.mergeProperties(Constants.RUNSYSTEMCAPABILITIES));
//		framework = getRunframework(project.getProperty(Constants.RUNFRAMEWORK));
//
//		timeout = Processor.getDuration(project.getProperty(Constants.RUNTIMEOUT), 0);
//		trace = Processor.isTrue(project.getProperty(Constants.RUNTRACE));
//
//		runpath.addAll(project.getRunFw());
//
//		for (Container c : runpath) {
//			addClasspath(c);
//		}
//
//		runvm.addAll(project.getRunVM());
//		runprogramargs.addAll(project.getRunProgramArgs());
//		runproperties = project.getRunProperties();
//
//		storageDir = project.getRunStorage();
//		if (storageDir == null) {
//			storageDir = new File(project.getTarget(), "fw");
//		}
//
//		setKeep(project.getRunKeep());
    }

    private int getRunframework(String property) {
        if (Constants.RUNFRAMEWORK_NONE.equalsIgnoreCase(property)) {
            return NONE;
        } else if (Constants.RUNFRAMEWORK_SERVICES.equalsIgnoreCase(property)) {
            return SERVICES;
        }

        return SERVICES;
    }

    public void addFrameworkJar(File file) {
        runPath.add(file.getAbsolutePath());
    }
    public void addClasspath(Container container) throws Exception {
        /*	if (container.getError() != null) {
         project.error("Cannot launch because %s has reported %s", container.getProject(), container.getError());
         } else {
         Collection<Container> members = container.getMembers();
         for (Container m : members) {
         String path = m.getFile().getAbsolutePath();
         if (!classpath.contains(path)) {

         Manifest manifest = m.getManifest();

         if (manifest != null) {

         // We are looking for any agents, used if
         // -javaagent=true is set
         String agentClassName = manifest.getMainAttributes().getValue("Premain-Class");
         if (agentClassName != null) {
         String agent = path;
         if (container.attributes != null && container.attributes.get("agent") != null) {
         agent += "=" + container.attributes.get("agent");
         }
         agents.add(path);
         }

         Parameters exports = project
         .parseHeader(manifest.getMainAttributes().getValue(Constants.EXPORT_PACKAGE));
         for (Entry<String,Attrs> e : exports.entrySet()) {
         if (!runsystempackages.containsKey(e.getKey()))
         runsystempackages.put(e.getKey(), e.getValue());
         }

         // Allow activators on the runpath. They are called
         // after
         // the framework is completely initialized wit the
         // system
         // context.
         String activator = manifest.getMainAttributes().getValue(EMBEDDED_ACTIVATOR);
         if (activator != null)
         activators.add(activator);
         }
         classpath.add(path);
         }
         }
         } */
    }

    protected void addClasspath(Collection<Container> path) throws Exception {
        for (Container c : Container.flatten(path)) {
            addClasspath(c);
        }
    }

    public void addRunBundle(String f) {
        runbundles.add(f);
    }

    public void addRunBundle(File f) {
        addRunBundle(f.getAbsolutePath());
    }

    public Collection<String> getRunBundles() {
        return runbundles;
    }

    public void addRunVM(String arg) {
        runvm.add(arg);
    }

    public void addRunProgramArgs(String arg) {
        runprogramargs.add(arg);
    }

    public List<String> getRunpath() {
        return runPath;
    }

    public Collection<String> getClasspath() {
        return runPath;
    }

    public Collection<String> getRunVM() {
        return runvm;
    }

    @Deprecated
    public Collection<String> getArguments() {
        return getRunProgramArgs();
    }

    public Collection<String> getRunProgramArgs() {
        return runprogramargs;
    }

    public Map<String, String> getRunProperties() {
        return runproperties;
    }

    public File getStorageDir() {
        return storageDir;
    }

    /**
     * launch a framework internally. I.e. do not start a separate process.
     * @throws Exception
     */
    static Pattern IGNORE = Pattern.compile("org(/|\\.)osgi(/|\\.).resource.*");

    /**
     * Is called after the process exists. Can you be used to cleanup the
     * properties file.
     */
    protected void reportResult(int result) {
        switch (result) {
            case OK:
                System.out.printf("Command terminated normal %s", java);
                break;
            case TIMEDOUT:
                System.out.printf("Launch timedout: %s", java);
                break;

            case ERROR:
                System.out.printf("Launch errored: %s", java);
                break;

            case WARNING:
                System.out.printf("Launch had a warning %s", java);
                break;
            default:
                System.out.printf("Exit code remote process %d: %s", result, java);
                break;
        }
    }

    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = unit.convert(timeout, TimeUnit.MILLISECONDS);
    }

    public long getTimeout() {
        return this.timeout;
    }

    public void cancel() throws Exception {
        java.cancel();
    }

    public Map<String, ? extends Map<String, String>> getSystemPackages() {
        return runsystempackages.asMapMap();
    }

    public String getSystemCapabilities() {
        return runsystemcapabilities.isEmpty() ? null : runsystemcapabilities.toString();
    }

    public Parameters getSystemCapabilitiesParameters() {
        return runsystemcapabilities;
    }

    public void setKeep(boolean keep) {
        this.keep = keep;
    }

    public boolean isKeep() {
        return keep;
    }

    public void setTrace(boolean level) {
        this.trace = level;
    }

    public boolean getTrace() {
        return this.trace;
    }

    public boolean addActivator(String e) {
        return activators.add(e);
    }

    public Collection<String> getActivators() {
        return Collections.unmodifiableCollection(activators);
    }

    /**
     * Either NONE or SERVICES to indicate how the remote end launches. NONE
     * means it should not use the classpath to run a framework. This likely
     * requires some dummy framework support. SERVICES means it should load the
     * framework from the claspath. @return
     */
    public int getRunFramework() {
        return framework;
    }

    public void setRunFramework(int n) {
        assert n == NONE || n == SERVICES;
        this.framework = n;
    }

    /**
     * Add the specification for a set of bundles the runpath if it does not
     * already is included. This can be used by subclasses to ensure the proper
     * jars are on the classpath. @param defaultSpec The default spec for
     * default jars
     */
    public void addDefault(String defaultSpec) throws Exception {
//		Collection<Container> deflts = project.getBundles(Strategy.HIGHEST, defaultSpec, null);
//		for (Container c : deflts)
//			addClasspath(c);
    }

    public File getCwd() {
        return cwd;
    }

    public void setCwd(File cwd) {
        this.cwd = cwd;
    }

    public static interface NotificationListener {

        void notify(NotificationType type, String notification);
    }

    public static enum NotificationType {

        ERROR, WARNING, INFO;
    }

    public void registerForNotifications(NotificationListener listener) {
        listeners.put(listener, Boolean.TRUE);
    }

    public Set<NotificationListener> getNotificationListeners() {
        return Collections.unmodifiableSet(listeners.keySet());
    }

    /**
     * Set the stderr and stdout streams for the output process. The debugged
     * process must append its output (i.e. write operation in the process under
     * debug) to the given appendables. @param out std out @param err std err
     */
    public void setStreams(Appendable out, Appendable err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Write text to the debugged process as if it came from stdin. @param text
     * the text to write @throws Exception
     */
    public void write(String text) throws Exception {

    }

    /**
     * Utility to calculate the final framework properties from settings
     */
    /**
     * This method should go to the ProjectLauncher @throws Exception
     */
    public void calculatedProperties(Map<String, Object> properties) throws Exception {

        if (!keep) {
            properties.put(org.osgi.framework.Constants.FRAMEWORK_STORAGE_CLEAN,
                    org.osgi.framework.Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        }

        if (!runsystemcapabilities.isEmpty()) {
            properties.put(org.osgi.framework.Constants.FRAMEWORK_SYSTEMCAPABILITIES_EXTRA,
                    runsystemcapabilities.toString());
        }

        if (!runsystempackages.isEmpty()) {
            properties.put(org.osgi.framework.Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, runsystempackages.toString());
        }

    }

}
