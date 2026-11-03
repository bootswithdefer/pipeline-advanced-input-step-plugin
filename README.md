# Pipeline: Advanced Input Step Plugin

Adds the Pipeline `advancedInput` step to wait for human input or approval, with an
optional third **skip** outcome in addition to proceed and abort.

This is a fork of the official
[Pipeline: Input Step](https://github.com/jenkinsci/pipeline-input-step-plugin) plugin.
It installs under a distinct artifact ID (`pipeline-advanced-input-step`), a distinct
Java package, and a distinct step name (`advancedInput`), so it **coexists** with the
stock `input` step rather than replacing it. Pipelines that want the skip outcome call
`advancedInput`; everything else keeps using `input`.

A Proceed or Abort option is shown in the stage view and inline in the build console.
The parameter entry screen can be accessed via a link at the bottom of the build console
log or via a link in the sidebar for a build.

Example with `Message Parameter`:

  advancedInput 'Proceed or Abort'


Example with `Custom identifier`:
Every input step has a unique identifier. It is used in the generated URL to proceed or abort.

A specific identifier could be used, for example, to mechanically respond to the input from some external process/tool.

   advancedInput id: '2', message: 'input-message'


Example with `Cancel Button Caption` and `OK Button Caption` (Optional):

   advancedInput message: 'input-message', cancel: 'Cancel', ok: 'OK'

Example with a `Skip` option (Optional):
Offers a third outcome alongside proceed and abort. Choosing it resumes the pipeline
with the configured `skipValue` (default `SKIPPED`) instead of proceeding or aborting,
so the pipeline can branch on the result. Skip requires the same permission as proceed
(Job/Build). When `skip` is omitted, only proceed and abort are shown.

   def result = advancedInput message: 'Apply the plan?', ok: 'Apply', skip: 'Skip apply', skipValue: 'SKIP', cancel: 'Abort'
   if (result == 'SKIP') {
     echo 'User chose to skip the apply'
   }


Example with `Allowed Submitter`:
Usernames and/or external group names of those permitted to respond to the input, separated by ','. Spaces will be trimmed automatically, so "Alice, Bob, Charles" is the same as "Alice,Bob,Charles".
Note: Jenkins administrators are able to respond to the input regardless of the value of this parameter. Users with [**Job/cancel** permission](https://www.jenkins.io/doc/book/security/access-control/permissions/#job-permissions) may also respond with 'Abort' to the input.

   advancedInput message: '', submitter: 'jenkins-user, jenkins-user2'

Example with Parameter to store the `Approving Submitter`:

If specified, this is the name of the return value that will contain the username of the user that approves this input. The return value will be handled in a fashion similar to the parameters value.

   advancedInput message: 'input-message', submitter: 'jenkins-submitter, jenkins-submitter2', submitterParameter: 'approvers-id-to-be-stored'


Use the [Pipeline Syntax Snippet Generator](https://www.jenkins.io/redirect/pipeline-snippet-generator) to select options for the `advancedInput` step.

## Building and releasing

See the `justfile` for build tasks (`just compile`, `just test`, `just package`). The
build runs on Maven 4 (via [mise](https://mise.jdx.dev/)) and JDK 21; the parent POM
targets Maven 4. Tagged releases publish the signed `.hpi` and `update-center.json` to a
GitHub Release — see `.github/workflows/release.yml`.
