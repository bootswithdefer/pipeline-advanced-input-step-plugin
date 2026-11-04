# Pipeline: Advanced Input Step Plugin

Adds the Pipeline `advancedInput` step to wait for human input or approval, with an
optional third **skip** outcome in addition to proceed and abort.

This is a fork of the official
[Pipeline: Input Step](https://github.com/jenkinsci/pipeline-input-step-plugin) plugin.
It installs under a distinct artifact ID (`pipeline-advanced-input-step`), Java package,
and step name (`advancedInput`), so it coexists with the stock `input` step rather than
replacing it. Pipelines that want the skip outcome call `advancedInput`; everything else
keeps using `input`.

The options are shown in the stage view and inline in the build console. The parameter
entry screen is linked from the bottom of the console log and from the build sidebar.

## Usage

Use the [Pipeline Syntax Snippet Generator](https://www.jenkins.io/redirect/pipeline-snippet-generator)
to build an `advancedInput` call interactively.

### Message

```groovy
advancedInput 'Proceed or Abort'
```

### Skip

Offers a third outcome alongside proceed and abort. Choosing it resumes the pipeline with
the configured `skipValue` (default `SKIPPED`), so the pipeline can branch on the result.
Skip requires the same permission as proceed (Job/Build). When `skip` is omitted, only
proceed and abort are shown.

```groovy
def result = advancedInput message: 'Apply the plan?', ok: 'Apply', skip: 'Skip apply', skipValue: 'SKIP', cancel: 'Abort'
if (result == 'SKIP') {
    echo 'User chose to skip the apply'
}
```

### Button captions

```groovy
advancedInput message: 'input-message', cancel: 'Cancel', ok: 'OK'
```

### Custom identifier

Every input step has a unique identifier, used in the generated URL to proceed or abort.
Setting one explicitly lets an external process respond to the input.

```groovy
advancedInput id: '2', message: 'input-message'
```

### Allowed submitters

A comma-separated list of usernames and/or external group names permitted to respond.
Surrounding spaces are trimmed, so `Alice, Bob` equals `Alice,Bob`.

```groovy
advancedInput message: '', submitter: 'jenkins-user, jenkins-user2'
```

> [!NOTE]
> Jenkins administrators can always respond. Users with
> [Job/Cancel permission](https://www.jenkins.io/doc/book/security/access-control/permissions/#job-permissions)
> can also respond with Abort.

### Recording the approver

`submitterParameter` names a return value that receives the username of the approving
user, handled like the other parameter values.

```groovy
advancedInput message: 'input-message', submitter: 'jenkins-submitter, jenkins-submitter2', submitterParameter: 'approvers-id-to-be-stored'
```

### Options

| Option | Description |
|---|---|
| `message` | Prompt shown to the user (required) |
| `id` | Custom identifier used in the proceed/abort URL |
| `ok` | Proceed button caption |
| `cancel` | Abort button caption |
| `skip` | Skip button caption; enables the skip outcome |
| `skipValue` | Value returned when skip is chosen (default `SKIPPED`) |
| `submitter` | Comma-separated users or groups allowed to respond |
| `submitterParameter` | Return value name that receives the approver's username |
| `parameters` | Parameter definitions to collect from the user |

## Building and releasing

The build uses Maven 4 (via [mise](https://mise.jdx.dev/)) and JDK 21; the parent POM
targets Maven 4. Build tasks live in the `justfile`:

```sh
just compile
just test
just package
```

Tagged releases publish the signed `.hpi` and `update-center.json` to a GitHub Release;
see `.github/workflows/release.yml`. Changes are tracked in [CHANGELOG.md](CHANGELOG.md).
