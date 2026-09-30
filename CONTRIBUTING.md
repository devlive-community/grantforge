<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Contributing Guidelines
We love improvements to our tools! There are a few key ways you can help us improve our projects:

Submitting Feedback, Requests, and Bugs
Our process for submitting feedback, feature requests, and reporting bugs usually begins by discussion on our chat and, after initial clarification, through GitHub issues. Each project repository generally maintains its own set of issues:

    https://github.com/devlive-community/grantforge/discussions

Some projects have additional templates or sets of questions for each issue, which you will be prompted to fill out when creating one.

Issues that span multiple projects or are about coordinating how we work overall are in the Overview Issue Tracker.

To know how to label an issue, have a look to https://github.com/devlive-community/grantforge/issues

Submitting Code and Documentation Changes
We still do not have project guidelines for all of the projects hosted in our GitHub Organization, which new repositories should follow during their creation.

Our process for accepting changes operates by Pull Request (PR) and has a few steps:

If you haven't submitted anything before, and you aren't (yet!) a member of our organization, fork and clone the repo:

$ git clone git@github.com:devlive-community/grantforge.git
Organization members should clone the upsteam repo, instead of working from a personal fork:

$ git clone git@github.com:devlive-community/grantforge.git
Create a new branch for the changes you want to work on. Choose a topic for your branch name that reflects the change:

$ git checkout -b <branch-name>
Create or modify the files with your changes. If you want to show other people work that isn't ready to merge in, commit your changes then create a pull request (PR) with WIP or Work In Progress in the title.

https://github.com/devlive-community/grantforge/pull/new/master
Once your changes are ready for final review, commit your changes then modify or create your pull request (PR), assign as a reviewer or ping (using "@<username>") a Lieutenant (someone able to merge in PRs) active on the project (all Lieutenants can be pinged via @devlive-community/grantforge)

Allow others sufficient time for review and comments before merging. We make use of GitHub's review feature to comment in-line on PRs when possible. There may be some fixes or adjustments you'll have to make based on feedback.

Once you have integrated comments, or waited for feedback, a Lieutenant should merge your changes in!

These guidelines are based on Tools for Government Data Archiving's.
