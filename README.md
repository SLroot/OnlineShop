# 🛒 Online Shop

A backend RESTful API for an e-commerce application built with **Java 21**, **Spring Boot**, **MySQL**, and **Maven**.

This project was developed to practice and demonstrate modern Java backend development, RESTful API design, database integration, layered architecture, authentication, authorization, and role-based access control.

---

## 📌 Overview

Online Shop is a backend application for an e-commerce platform.

The application provides the foundation for managing users, products, shopping carts, and orders through RESTful APIs.

The project is built with a focus on:

- Clean and maintainable code
- Layered architecture
- Separation of concerns
- Database persistence with JPA/Hibernate
- Role-based authorization
- RESTful API design
- Object-oriented programming with Java

---

## 🚀 Features

### 👤 User Management

- User registration
- User authentication
- User management
- User role management
- User authorization

### 🔐 Authentication & Authorization

The application supports role-based access control (RBAC).

Available roles:

- `USER`
- `SELLER`
- `MANAGER`
- `ADMIN`

Each role can have different permissions depending on the protected resource.

### 📦 Product Management

- Create products
- Update products
- Delete products
- Retrieve product information
- List products
- Role-based product management

### 🛍️ Shopping

- Browse available products
- View product details
- Manage shopping cart
- Add products to cart
- Remove products from cart

### 📋 Order Management

- Create orders
- Manage orders
- Retrieve order information
- Role-based order management

---

# 🧰 Tech Stack

| Technology | Version |
|------------|---------|
| Java | 21 |
| Spring Boot | 3.x |
| Spring Web | REST API |
| Spring Data JPA | Database Access |
| Hibernate | ORM |
| MySQL | 8.x |
| Maven | Build & Dependency Management |
| Git | Version Control |
| GitHub | Repository Hosting |

---

# 🏗️ Architecture

The application follows a layered architecture.

```text
┌──────────────────────┐
│      Controller      │
│    REST Endpoints    │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│       Service        │
│   Business Logic     │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│     Repository       │
│   Data Access Layer  │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│        MySQL         │
│       Database       │
└──────────────────────┘
