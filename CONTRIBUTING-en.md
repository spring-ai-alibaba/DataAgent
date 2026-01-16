[中文](./CONTRIBUTING-zh.md) | English

## How to Contribute

## Thank you for contributing to Spring AI Alibaba-DataAgent!

Since the beginning of open-source development, DataAgent has received a lot of attention from the community. Every issue and PR from the community helps the project and contributes to building a better Spring AI.

We sincerely thank all developers who have submitted issues and PRs to this project. We hope more developers from the community will join us in making this project better.

## How to Contribute

Before contributing code, please take a moment to understand the process for contributing to Spring AI Alibaba.

### What to Contribute?

We welcome any contributions at any time, whether it's a simple typo fix, bug fix, or new feature. Please feel free to raise issues or submit PRs. We also value documentation and integration with other open-source projects, and welcome contributions in these areas.

For complex modifications, we recommend adding a Feature label in an Issue first, with a brief description of the design and changes.

### Where to Start?

If you're a first-time contributor, you can start with tasks from [good first issue](https://github.com/spring-ai-alibaba/DataAgent/labels/good%20first%20issue) and [help wanted](https://github.com/spring-ai-alibaba/DataAgent/labels/help%20wanted).

### Fork the Repository and Clone It Locally

- Click the `Fork` icon in the upper right corner of [this project](https://github.com/spring-ai-alibaba/DataAgent) to fork alibaba/spring-ai-alibaba to your own space.
- Clone the spring-ai-alibaba repository from your account locally. For example, if your account is `chickenlj`, run `git clone https://github.com/chickenlj/DataAgent.git` to perform the clone operation.

### Configure Github Information

- Run `git config --list` on your machine to view your global git username and email.
- Check if the displayed user.name and user.email match your github username and email.
- If your company has its own gitlab or uses other commercial gitlab, there may be a mismatch. In this case, you need to set a separate username and email for the spring-ai-alibaba project.
- For instructions on setting username and email, please refer to the official github documentation: [Setting your username](https://help.github.com/articles/setting-your-username-in-git/#setting-your-git-username-for-a-single-repository), [Setting your email](https://help.github.com/articles/setting-your-commit-email-address-in-git/).

### Merge Latest Code

After forking, the original repository's main branch may have new commits. To avoid conflicts between your PR and the main branch, you need to merge the main branch in a timely manner.

- In your local spring-ai-alibaba directory, run `git remote add upstream https://github.com/spring-ai-alibaba/DataAgent` to add the original repository address to the remote stream.
- In your local spring-ai-alibaba directory, run `git fetch upstream` to fetch the remote stream locally.
- In your local spring-ai-alibaba directory, run `git checkout main` to switch to the main branch.
- In your local spring-ai-alibaba directory, run `git rebase upstream/main` to rebase the latest code.

### Configure Spring AI Standard Code Format

As an implementation of Spring AI, Spring AI Alibaba directly follows the Spring AI project code standards. Please refer to the relevant code format specifications before starting. You need to configure the code format before submitting code.

### Development

Develop your feature. **After development, we recommend using `mvn clean package` to ensure the modified code compiles locally. This command also automatically formats the code in Spring style**. Then submit the code. Before submitting, create a new branch for this feature and use that branch for code submission.

### Local CI

After completing local development, we strongly recommend running the `make` command provided in the project's `CI\make` directory for local continuous integration (CI) checks before submitting a PR, to ensure the code meets project standards and specifications. If you have any questions about local CI, you can enter `make help` in the console to learn more.

### Local Checkstyle

To reduce unnecessary code style issues, Spring AI Alibaba provides local Checkstyle checking. You can run `mvn checkstyle:check` in the project root directory to check if the code style meets the specifications.

### Remove Unused Imports

To ensure code cleanliness, please remove unused imports from Java files. You can run `mvn spotless:apply` to automatically remove unused imports.

### Submit Latest Code

After coding is complete, you need to format and check the commit message based on the PR specification `[lint-pr-title.yml](.github/workflows/lint-pr-title.yml)` to ensure the commit message meets the specifications.
Commit format: git commit -m "type(module): space followed by compliant commit message", for example `feat(docs): update contribute-zh`

### Merge Latest Code

- Similarly, before submitting a PR, you need to rebase the main branch code (or the corresponding target branch if your target branch is not main). Please refer to the previous section for specific steps.
- If there are conflicts, resolve them first.

### Submit PR

Submit a PR, describe the changes and implemented features according to the `Pull request template`, and wait for code review and merge to become a Spring AI Alibaba Contributor and contribute to a better Spring AI Alibaba.
