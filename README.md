# BloodDononerApp — Blood Donor Management System

### Android Application | Kotlin | XML | Firebase

**BloodDononerApp** is an Android-based Blood Donor Management System designed to connect blood donors, recipients, hospital staff, and system administrators through a centralized digital platform.

The application aims to simplify blood donor discovery, emergency blood requests, donation management, hospital coordination, and blood inventory monitoring.

Developed using **Kotlin, Android XML, and Firebase**, the system provides three role-based modules: **User, Staff, and Admin**.

---

## Key Features

### User Module
- User Registration and Login
- Google Sign-In Authentication
- User Profile Management
- Search Blood Donors by Blood Group and Location
- View Donor Information
- Check Blood Bank Availability
- Create Emergency Blood Requests
- Blood Donation Eligibility Checking
- Donation History Tracking
- Donation Availability Management
- Notifications and Help & Support

### Staff Module
- Staff Dashboard
- Appointment Management
- Donor Check-In and Screening
- Record Blood Donations
- View Donor Records
- Manage Emergency Blood Requests
- Donor Matching
- Hospital Blood Inventory Monitoring
- Staff Notifications and Profile Management

### Admin Module
- Admin Dashboard
- Hospital Management
- Staff Account Management
- User Account Management
- Emergency Request Monitoring
- Blood Inventory Management
- Donation Record Management
- Notifications Management
- System Reports Generation
- Administrative Profile and Account Management

---

## Technology Stack

| Technology | Purpose |
|---|---|
| Kotlin | Android Application Development |
| XML | User Interface Design |
| Android Studio | Development Environment |
| Firebase Authentication | User Authentication |
| Cloud Firestore | Cloud Database |
| Gradle | Build and Dependency Management |
| Git & GitHub | Version Control and Collaboration |

---

## Project Architecture

The application follows a role-based structure with separate interfaces and functionality for Users, Staff, and Administrators.

```text
BloodDononerApp-Development/
|
|-- app/
|   |-- src/
|   |   |-- main/
|   |       |-- java/
|   |       |   |-- com/example/blooddononerapp/
|   |       |       |-- Kotlin Source Files
|   |       |
|   |       |-- res/
|   |       |   |-- layout/
|   |       |   |-- drawable/
|   |       |   |-- mipmap/
|   |       |   |-- values/
|   |       |
|   |       |-- AndroidManifest.xml
|   |
|   |-- build.gradle.kts
|
|-- gradle/
|-- build.gradle.kts
|-- settings.gradle.kts
|-- gradle.properties
|-- README.md
```

---

## GitHub Branch Organization

The project is organized into **20 feature branches** to support modular development and version control.

### User Features
1. `feature/onboarding`
2. `feature/authentication`
3. `feature/profile-setup`
4. `feature/user-home`
5. `feature/donor-search`
6. `feature/donor-details`
7. `feature/blood-bank`
8. `feature/emergency-requests`
9. `feature/donation`
10. `feature/donation-history`
11. `feature/user-profile`
12. `feature/user-notifications`

### Staff Features
13. `feature/staff-dashboard`
14. `feature/staff-appointments`
15. `feature/staff-donations`
16. `feature/staff-emergency`
17. `feature/staff-inventory`

### Admin Features
18. `feature/admin-dashboard`
19. `feature/admin-management`
20. `feature/admin-operations`

Feature branches are integrated into the `main` branch through GitHub Pull Requests.

---

## Installation and Setup

### Prerequisites

- Android Studio
- Android SDK
- JDK compatible with the Gradle configuration
- Firebase Project Access
- Android Emulator or Physical Android Device

### Getting Started

**1. Clone the Repository**

```bash
git clone https://github.com/Amila1103/BloodDononerApp-Development.git
```

**2. Open in Android Studio**

Open Android Studio and select the cloned project directory.

**3. Configure Firebase**

Connect the application to an authorized Firebase project and place its matching `google-services.json` file inside the `app/` directory.

Enable the required authentication providers and configure Firestore security rules appropriately.

**4. Sync Gradle**

Allow Android Studio to download and synchronize the required dependencies.

**5. Run the Application**

Select an Android Emulator or a connected physical Android device and click **Run**.

---

## Security Considerations

- Firebase Authentication for account access
- Role-based access for User, Staff, and Admin modules
- Firestore Security Rules for database access control
- Account suspension and activation management
- Secure handling of authentication sessions

Production deployment requires appropriate security testing and configuration review.

---

## Project Objectives

- Make blood donor discovery more accessible.
- Reduce delays in emergency blood requests.
- Improve communication between donors and recipients.
- Support hospital staff in blood donation workflows.
- Provide administrators with centralized management tools.
- Improve visibility of hospital blood inventory.

---

## Project Status

**Development and Integration Testing**

The repository contains the Android application modules organized through feature-based GitHub development workflows. Final build verification, integration testing, and deployment validation should be completed before production use.

---

## Maintainer

**Amila1103**

GitHub: [github.com/Amila1103](https://github.com/Amila1103)

Project Repository: [BloodDononerApp-Development](https://github.com/Amila1103/BloodDononerApp-Development)

---

### Donate Blood. Save Lives.

*Connecting donors, recipients, and healthcare teams through technology.*
