package io.bit3.mgpm.config.parser;

import static io.bit3.mgpm.config.parser.Asserts.assertIsBoolean;
import static io.bit3.mgpm.config.parser.Asserts.assertIsList;
import static io.bit3.mgpm.config.parser.Asserts.assertIsMap;
import static io.bit3.mgpm.config.parser.Asserts.assertIsString;
import static io.bit3.mgpm.config.parser.Asserts.assertNotEmpty;

import io.bit3.mgpm.config.Config;
import io.bit3.mgpm.config.GitConfig;
import io.bit3.mgpm.config.GithubConfig;
import io.bit3.mgpm.config.InvalidConfigException;
import io.bit3.mgpm.config.RepositoryConfig;
import io.bit3.mgpm.config.Strategy;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabProject;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.IOException;
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

      config.getRepositories().add(new RepositoryConfig(name, url, Strategy.HEAD));
    }

    private void configureGithubRepositories(Config config, int repositoryIndex, Map<Object, Object> map) {
      String owner = castGithubRepositoryOwnerValue(map.get("owner"), repositoryIndex);
      String namePattern = castGithubRepositoryNamesPatternValue(map.get("name"), repositoryIndex);
      List<Repository> repositories = fetchGithubRepositories(config, owner, namePattern);

      for (Repository repository : repositories) {
        String url = repository.getCloneUrl();

        config.getRepositories().add(new RepositoryConfig(repository.getName(), url, Strategy.HEAD));
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
      String projectPattern = castGitlabRepositoryProjectPatternValue(map.get("name"), repositoryIndex);
      boolean includeArchived = castGitlabRepositoryArchivedValue(map.get("archived"), repositoryIndex);

      List<GitlabProject> projects = fetchGitlabProjects(
          config, hostUrl, token, namespace, projectPattern);

      projects.sort((p1, p2) -> p1.getName().compareToIgnoreCase(p2.getName()));

      for (GitlabProject project : projects) {
        // skip archived projects
        if (!includeArchived && project.isArchived()) {
          continue;
        }

        String url = project.getSshUrl();

        config.getRepositories().add(new RepositoryConfig(project.getPath(), url, Strategy.HEAD));
      }
    }

    private List<GitlabProject> fetchGitlabProjects(Config config, String hostUrl, String token,
                                                    String namespace, String projectPattern) {
      try {

        GitlabAPI api = GitlabAPI.connect(hostUrl, token);

        Pattern pattern = Pattern.compile(projectPattern);

        return api.getProjects()
            .stream()
            .filter(gitlabProject -> namespace.equals(gitlabProject.getNamespace().getName()))
            .filter(gitlabProject -> pattern.matcher(gitlabProject.getName()).matches())
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
  }
}
