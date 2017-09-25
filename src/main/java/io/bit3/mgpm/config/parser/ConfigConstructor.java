package io.bit3.mgpm.config.parser;

import static io.bit3.mgpm.config.parser.Asserts.assertEndsWith;
import static io.bit3.mgpm.config.parser.Asserts.assertIsBoolean;
import static io.bit3.mgpm.config.parser.Asserts.assertIsList;
import static io.bit3.mgpm.config.parser.Asserts.assertIsMap;
import static io.bit3.mgpm.config.parser.Asserts.assertIsString;
import static io.bit3.mgpm.config.parser.Asserts.assertMatch;
import static io.bit3.mgpm.config.parser.Asserts.assertNotEmpty;
import static io.bit3.mgpm.config.parser.Asserts.assertStartsWith;
import static io.bit3.mgpm.config.parser.Asserts.assertPath;

import io.bit3.mgpm.config.Config;
import io.bit3.mgpm.config.GitConfig;
import io.bit3.mgpm.config.GithubConfig;
import io.bit3.mgpm.config.InvalidConfigException;
import io.bit3.mgpm.config.RepositoryConfig;
import io.bit3.mgpm.config.Strategy;

import org.apache.commons.lang.StringUtils;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabProject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigConstructor extends Constructor {
  private final Config config;

  public ConfigConstructor(Config config) {
    this.config = config;

    this.rootTag = new Tag(Config.class);
    this.yamlConstructors.put(this.rootTag, new ConstructConfig());
  }

  private class ConstructConfig extends AbstractConstruct {
    public Object construct(Node node) {
      Map<Object, Object> map = castConfigRootValue(node);

      // git
      Map<String, Object> gitConfig = castGitConfigValue(map.get("github"));
      configureGit(config, gitConfig);

      // github
      Map<String, Object> githubConfig = castGithubConfigValue(map.get("github"));
      configureGithub(config, githubConfig);

      // repositories
      List<Object> repositories = castRepositoriesValue(map.get("repositories"));
      configureRepositories(config, repositories);

      return config;
    }

    private void configureGit(Config config, Map<String, Object> map) {
      GitConfig gitConfig = config.getGitConfig();
      gitConfig.setBinary(castGitConfigBinaryValue(map.get("bin")));
    }

    private void configureGithub(Config config, Map<String, Object> map) {
      GithubConfig githubConfig = config.getGithubConfig();
      githubConfig.setToken(castGithubConfigTokenValue(map.get("token")));
    }

    private void configureRepositories(Config config, List<Object> repositories) {
      int repositoryIndex = 0;
      for (Object item : repositories) {
        configureRepository(config, repositoryIndex, castRepositoryValue(item, repositoryIndex));
        repositoryIndex++;
      }
    }

    private void configureRepository(Config config, int repositoryIndex, Map<Object, Object> map) {
      String type = castRepositoryTypeValue(map.get("type"), repositoryIndex);

      if ("git".equals(type)) {
        configureGitRepository(config, repositoryIndex, map);
        return;
      }

      if ("cgit".equals(type)) {
        configureCgitRepositories(config, repositoryIndex, map);
        return;
      }

      if ("github".equals(type)) {
        configureGithubRepositories(config, repositoryIndex, map);
        return;
      }

      if ("gitlab".equals(type)) {
        configureGitlabRepositories(config, repositoryIndex, map);
        return;
      }

      throw new InvalidConfigException(
          String.format("repsitories[%d].type the type \"%s\" is not known", repositoryIndex, type)
      );
    }

    private void configureGitRepository(Config config, int repositoryIndex, Map<Object, Object> map) {
      String url = castRepositoryUrlValue(map.get("url"), repositoryIndex);
      String name = castRepositoryNameValue(map.get("name"), repositoryIndex);
      String path = castRepositoryPathValue(map.get("path"), repositoryIndex);

      File parentDir = new File(Paths.get(".").toAbsolutePath().normalize().toString());
      if (StringUtils.isNotEmpty(path)) {
        parentDir = new File(parentDir, path);
      }
      File directory = new File(parentDir, name);

      RepositoryConfig repositoryConfig = new RepositoryConfig(
          path,
          name,
          url,
          Strategy.HEAD,
          directory
      );
      config.getRepositories().add(repositoryConfig);
    }

    private void configureCgitRepositories(Config config, int repositoryIndex, Map<Object, Object> map) {
      final URL baseUrl = castCgitBaseUrlValue(map.get("baseUrl"), repositoryIndex);
      final String pathPrefix = castCgitPathPrefixValue(map.get("pathPrefix"), repositoryIndex);
      final String sshUser = castCgitSshUserValue(map.get("sshUser"), repositoryIndex);
      final String join = castCgitJoinValue(map.get("join"), repositoryIndex);
      final String path = castCgitPathValue(map.get("path"), repositoryIndex);

      File parentDir = new File(Paths.get(".").toAbsolutePath().normalize().toString());
      if (StringUtils.isNotEmpty(path)) {
        parentDir = new File(parentDir, path);
      }

      try {
        Document doc = Jsoup.connect(baseUrl.toString()).get();
        final Elements links = doc.select("td.sublevel-repo a[href^=\"" + pathPrefix + "\"]");

        for (Element link : links) {
          final String fullPath = link.attr("href").replaceFirst("/$", "");
          String localPath = fullPath.substring(pathPrefix.length()).replaceFirst("\\.git$", "");

          if (StringUtils.isNotEmpty(join)) {
            localPath = localPath.replace("/", join);
          }

          String url = String.format(
              "ssh://%s@%s%s",
              sshUser,
              baseUrl.getHost(),
              Paths.get(baseUrl.getPath() + "/" + fullPath).normalize().toString()
          );
          File projectDirectory = Paths.get(parentDir.getAbsolutePath() + "/" + localPath).toAbsolutePath().normalize().toFile();

          RepositoryConfig repositoryConfig = new RepositoryConfig(
              path,
              localPath,
              url,
              Strategy.HEAD,
              projectDirectory
          );

          config.getRepositories().add(repositoryConfig);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void configureGithubRepositories(Config config, int repositoryIndex, Map<Object, Object> map) {
      String owner = castGithubRepositoryOwnerValue(map.get("owner"), repositoryIndex);
      String namePattern = castGithubRepositoryNamesPatternValue(map.get("name"), repositoryIndex);
      String path = castGithubPathValue(map.get("path"), repositoryIndex);

      List<Repository> repositories = fetchGithubRepositories(config, owner, namePattern);

      File parentDir = new File(Paths.get(".").toAbsolutePath().normalize().toString());
      if (StringUtils.isNotEmpty(path)) {
        parentDir = new File(parentDir, path);
      }

      for (Repository repository : repositories) {
        String repositoryName = repository.getName();
        String url = repository.getSshUrl();
        File projectDirectory = new File(parentDir, repositoryName);
        RepositoryConfig repositoryConfig = new RepositoryConfig(
            path,
            repositoryName,
            url,
            Strategy.HEAD,
            projectDirectory
        );

        config.getRepositories().add(repositoryConfig);
      }
    }

    private List<Repository> fetchGithubRepositories(Config config, String owner,
                                                     String namePattern) {
      try {
        GithubConfig githubConfig = config.getGithubConfig();
        String githubToken = githubConfig.getToken();

        GitHubClient client = new GitHubClient();
        if (null != githubToken && !githubToken.isEmpty()) {
          client.setOAuth2Token(githubToken);
        }

        RepositoryService service = new RepositoryService(client);
        List<Repository> repositories = service.getRepositories(owner);
        Pattern pattern = Pattern.compile(namePattern);

        repositories.sort((r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

        return repositories
            .stream()
            .filter(r -> pattern.matcher(r.getName()).matches())
            .collect(Collectors.toList());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void configureGitlabRepositories(Config config, int repositoryIndex, Map<Object, Object> map) {
      String hostUrl = castGitlabRepositoryHostUrlValue(map.get("url"), repositoryIndex);
      String token = castGitlabRepositoryTokenValue(map.get("token"), repositoryIndex);
      String namespace = castGitlabRepositoryNamespaceValue(map.get("namespace"), repositoryIndex);
      String projectPattern = castGitlabRepositoryProjectPatternValue(map.get("project"), repositoryIndex);
      boolean includeArchived = castGitlabRepositoryArchivedValue(map.get("archived"), repositoryIndex);
      String path = castGitlabPathValue(map.get("path"), repositoryIndex);

      List<GitlabProject> projects = fetchGitlabProjects(
          config, hostUrl, token, namespace, projectPattern);

      projects.sort((p1, p2) -> p1.getName().compareToIgnoreCase(p2.getName()));

      Path parentDirPath = Paths.get(".").toAbsolutePath().normalize();
      if (StringUtils.isNotEmpty(path)) {
        parentDirPath = parentDirPath.resolve(path);
      }

      for (GitlabProject project : projects) {
        // skip archived projects
        if (!includeArchived && project.isArchived()) {
          continue;
        }

        String projectName = project.getName();
        String url = project.getSshUrl();
        String projectPathFragment = project.getPathWithNamespace().replaceFirst("^" + Pattern.quote(namespace) + "/", "");
        File projectDirectory = parentDirPath.resolve(projectPathFragment).toFile();
        RepositoryConfig repositoryConfig = new RepositoryConfig(
            path,
            projectName,
            url,
            Strategy.HEAD,
            projectDirectory
        );

        config.getRepositories().add(repositoryConfig);
      }
    }

    private List<GitlabProject> fetchGitlabProjects(Config config, String hostUrl, String token,
                                                    String namespace, String projectPattern) {
      try {

        GitlabAPI api = GitlabAPI.connect(hostUrl, token);

        Pattern pattern = Pattern.compile(Pattern.quote(namespace) + "/" + projectPattern);

        return api.getProjects()
            .stream()
            .filter(gitlabProject -> pattern.matcher(gitlabProject.getPathWithNamespace()).matches())
            .collect(Collectors.toList());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private Map<Object, Object> castConfigRootValue(Node node) {
      if (!NodeId.mapping.equals(node.getNodeId())) {
        throw new InvalidConfigException("Config must be a map on root-level");
      }

      return constructMapping((MappingNode) node);
    }

    private Map<String, Object> castGitConfigValue(Object object) {
      if (null == object) {
        return Collections.emptyMap();
      }

      assertIsMap(object, "git must be a map");

      return (Map<String, Object>) object;
    }

    private String castGitConfigBinaryValue(Object object) {
      if (null == object) {
        return "git";
      }

      assertIsString(object, "git.bin must be a string");
      assertNotEmpty(object, "git.bin must not be empty");

      return (String) object;
    }

    private Map<String, Object> castGithubConfigValue(Object object) {
      if (null == object) {
        return Collections.emptyMap();
      }

      assertIsMap(object, "github must be a map");

      return (Map<String, Object>) object;
    }

    private String castGithubConfigTokenValue(Object object) {
      if (null == object) {
        return null;
      }

      assertIsString(object, "github.token must be a string");
      assertNotEmpty(object, "github.token must not be empty");

      return (String) object;
    }

    private List<Object> castRepositoriesValue(Object object) {
      if (null == object) {
        return Collections.emptyList();
      }

      assertIsList(object, "repositories must be a list");

      return (List<Object>) object;
    }

    private Map<Object, Object> castRepositoryValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d] must not be empty", repositoryIndex);
      assertIsMap(object, "repsitories[%d] must be a map", repositoryIndex);

      return (Map<Object, Object>) object;
    }

    private String castRepositoryTypeValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].type must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].type must be a string", repositoryIndex);

      return (String) object;
    }

    private String castRepositoryUrlValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].url must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].url must be a string", repositoryIndex);

      return (String) object;
    }

    private String castRepositoryNameValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].name must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].name must be a string", repositoryIndex);

      return (String) object;
    }

    private String castRepositoryPathValue(Object object, int repositoryIndex) {
      if (null == object) {
        object = "";
      }

      assertIsString(object, "repsitories[%d].path must be a string", repositoryIndex);

      return (String) object;
    }

    private URL castCgitBaseUrlValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].baseUrl must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].baseUrl must be a string", repositoryIndex);

      try {
        return new URL((String) object);
      } catch (MalformedURLException e) {
        throw new InvalidConfigException(
            String.format("repsitories[%d].baseUrl must be a valid url", repositoryIndex),
            e
        );
      }
    }

    private String castCgitPathPrefixValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].pathPrefix must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].pathPrefix must be a string", repositoryIndex);

      final String string = (String) object;

      assertStartsWith(string, "/", "repsitories[%d].pathPrefix must start with a /", repositoryIndex);
      assertEndsWith(string, "/", "repsitories[%d].pathPrefix must ends with a /", repositoryIndex);
      assertPath(string, "repsitories[%d].pathPrefix must be a valid, normalized path", repositoryIndex);

      return string;
    }

    private String castCgitSshUserValue(Object object, int repositoryIndex) {
      if (null == object || "".equals(object.toString().trim())) {
        return "git";
      }

      assertIsString(object, "repsitories[%d].sshUser must be a string", repositoryIndex);

      return (String) object;
    }

    private String castCgitJoinValue(Object object, int repositoryIndex) {
      if (null == object) {
        object = "";
      }

      assertIsString(object, "repsitories[%d].join must be a string", repositoryIndex);
      assertMatch((String) object, "[\\w\\- ]+", "repsitories[%d].join must not contain special characters", repositoryIndex);

      return (String) object;
    }

    private String castCgitPathValue(Object object, int repositoryIndex) {
      if (null == object) {
        object = "";
      }

      assertIsString(object, "repsitories[%d].path must be a string", repositoryIndex);

      return (String) object;
    }

    private String castGithubRepositoryOwnerValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].owner must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].owner must be a string", repositoryIndex);

      return (String) object;
    }

    private String castGithubRepositoryNamesPatternValue(Object object, int repositoryIndex) {
      if (null == object) {
        object = ".+";
      }

      assertIsString(object, "repsitories[%d].name must be a string", repositoryIndex);

      return (String) object;
    }

    private String castGithubPathValue(Object object, int repositoryIndex) {
      if (null == object) {
        object = "";
      }

      assertIsString(object, "repsitories[%d].path must be a string", repositoryIndex);

      return (String) object;
    }

    private String castGitlabRepositoryHostUrlValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].url must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].url must be a string", repositoryIndex);

      return (String) object;
    }

    private String castGitlabRepositoryTokenValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].token must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].token must be a string", repositoryIndex);

      return (String) object;
    }

    private String castGitlabRepositoryNamespaceValue(Object object, int repositoryIndex) {
      assertNotEmpty(object, "repsitories[%d].namespace must not be empty", repositoryIndex);
      assertIsString(object, "repsitories[%d].namespace must be a string", repositoryIndex);

      return (String) object;
    }

    private String castGitlabRepositoryProjectPatternValue(Object object, int repositoryIndex) {
      if (null == object) {
        object = ".+";
      }

      assertIsString(object, "repsitories[%d].project must be a string", repositoryIndex);

      return (String) object;
    }

    private Boolean castGitlabRepositoryArchivedValue(Object object, int repositoryIndex) {
      if (null == object) {
        return false;
      }

      assertIsBoolean(object, "repsitories[%d].project must be a boolean", repositoryIndex);

      return (Boolean) object;
    }

    private String castGitlabPathValue(Object object, int repositoryIndex) {
      if (null == object) {
        object = "";
      }

      assertIsString(object, "repsitories[%d].path must be a string", repositoryIndex);

      return (String) object;
    }
  }
}
